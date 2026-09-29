/**
 * 환자 모니터 방식의 스윕(sweep) 파형 차트 (ECharts).
 * 대시보드/재생 화면의 ECG·가속도 차트가 공통으로 사용한다.
 *
 * - 화면 폭 = windowSeconds초 분량의 고정 길이 원형 버퍼(seriesData). 새 샘플은 왼쪽→오른쪽으로
 *   현재 위치(chartIndex)에 덮어쓰고, 끝에 닿으면 다시 왼쪽부터 덮어쓴다 (파형이 흘러가지 않고 제자리에 그려짐).
 * - 현재 위치 바로 앞의 일정 구간(ERASER)을 null로 비워 "지우개" 틈을 만든다 - 옛 파형과 새 파형의 경계.
 * - 실시간 데이터는 1초 단위 묶음으로 오므로 곧바로 그리지 않고 큐(dataQueue)에 쌓아 두었다가,
 *   FPS 간격으로 샘플링 속도에 맞는 만큼(chunk)만 꺼내 그린다 - 묶음 단위로 뚝뚝 끊기지 않고 부드럽게 그려진다.
 *   큐가 밀리면 chunk를 키워 따라잡는다 (calculateChunkSize).
 * - 재생 화면은 큐 없이 renderUpTo로 "특정 시점까지의 샘플"을 원형 버퍼 위치에 그대로 배치한다
 *   (절대 샘플 번호 % 버퍼 길이 = 위치이므로, 탐색(seek)해도 실시간과 같은 모습으로 그려진다).
 *
 * 한 화면에 차트가 여러 개(대상 카드 수 × 2)여도 requestAnimationFrame 루프는 하나만 돌린다.
 */
var SweepChart = (function () {
	"use strict";

	var FPS = 25;
	var FRAME_INTERVAL = 1000 / FPS;
	var GRID_SPLITS_PER_SECOND = 5; // 0.2초 간격 세로 격자 (심전도 기록지의 큰 칸)
	var ERASER_RATIO = 0.8; // 지우개 너비 = 격자 한 칸의 80%
	var GRID_COLOR = "rgba(148, 163, 184, 0.12)";
	var SCALE_COLOR = "rgba(255, 255, 255, 0.3)";

	var activeCharts = [];
	var loopRunning = false;
	var lastFrameTime = 0;

	/**
	 * @param container 차트를 그릴 div
	 * @param options {
	 *   samplingRateHz, windowSeconds, yMin, yMax, yInterval,
	 *   series: [{color, transform(value) -> 그릴 값}],  // 샘플 한 건 = 시리즈 수만큼의 값 배열
	 *   scaleMarkers: true면 1mv/1sec 눈금 표시 (ECG 전용)
	 * }
	 */
	function SweepChart(container, options) {
		this.container = container;
		this.options = options;
		this.dataQueue = [];
		this.chart = echarts.init(container);
		this._configure(options.samplingRateHz);

		var chart = this.chart;
		if (window.ResizeObserver) {
			// 대시보드 카드는 측정 인원/패널 크기에 따라 크기가 바뀌므로 창 resize만으로는 부족하다.
			this.resizeObserver = new ResizeObserver(function () {
				chart.resize();
			});
			this.resizeObserver.observe(container);
		}
		activeCharts.push(this);
		startLoop();
	}

	/** 샘플링 속도에 따라 버퍼 길이/격자/프레임당 chunk 크기를 정하고 차트를 새로 그린다. */
	SweepChart.prototype._configure = function (samplingRateHz) {
		var o = this.options;
		this.samplingRateHz = samplingRateHz;
		this.xLength = Math.round(samplingRateHz * o.windowSeconds);
		this.xSplit = samplingRateHz / GRID_SPLITS_PER_SECOND;
		this.eraserSize = Math.max(1, Math.round(this.xSplit * ERASER_RATIO));
		this.targetChunkSize = Math.max(1, Math.round(samplingRateHz / FPS));
		this.maxChunkSize = Math.ceil(this.targetChunkSize * 1.5);
		this.chartIndex = 0;
		this.dataQueue = [];

		var xLength = this.xLength;
		this.seriesData = o.series.map(function () {
			return new Array(xLength).fill(null);
		});

		var xSplit = this.xSplit;
		var xAxisData = [];
		for (var i = 0; i < xLength; i++) {
			xAxisData.push(i);
		}
		var self = this;
		this.chart.setOption({
			animation: false,
			tooltip: {show: false},
			grid: {left: 2, right: 2, top: 2, bottom: 2, containLabel: false},
			xAxis: {
				type: "category",
				data: xAxisData,
				show: true,
				axisLine: {show: false},
				axisTick: {show: false},
				axisLabel: {show: false},
				splitLine: {
					show: true,
					interval: function (idx) {
						return Math.round(idx % xSplit) === 0;
					},
					lineStyle: {color: GRID_COLOR, type: "solid", width: 1}
				}
			},
			yAxis: {
				type: "value",
				min: o.yMin,
				max: o.yMax,
				interval: o.yInterval,
				show: true,
				axisLine: {show: false},
				axisTick: {show: false},
				axisLabel: {show: false},
				splitLine: {show: true, lineStyle: {color: GRID_COLOR, type: "solid", width: 1}}
			},
			series: o.series.map(function (s, idx) {
				var series = {
					type: "line",
					showSymbol: false,
					silent: true,
					data: self.seriesData[idx],
					lineStyle: {color: s.color, width: 1.3}
				};
				if (o.scaleMarkers && idx === 0) {
					series.markLine = {symbol: "none", silent: true, data: buildScaleMarkers(xLength, xSplit, o.yMin, o.yMax)};
				}
				return series;
			})
		}, true);
	};

	/** 실시간 샘플이 들어온 샘플링 속도가 지금 버퍼 기준과 다르면 버퍼를 새 속도로 다시 만든다. */
	SweepChart.prototype.ensureSamplingRate = function (samplingRateHz) {
		if (samplingRateHz && samplingRateHz !== this.samplingRateHz) {
			this._configure(samplingRateHz);
		}
	};

	/** 실시간: 샘플(시리즈별 값 배열) 목록을 큐에 쌓는다. 실제 그리기는 프레임 루프가 나눠서 한다. */
	SweepChart.prototype.enqueue = function (samples) {
		for (var i = 0; i < samples.length; i++) {
			this.dataQueue.push(samples[i]);
		}
	};

	/**
	 * 재생: 절대 샘플 번호 endIndex(=지금까지 재생된 샘플 수) 직전까지의 최근 샘플들을
	 * 원형 버퍼의 해당 위치(절대 번호 % 버퍼 길이)에 배치해 한 번에 그린다.
	 * recentSamples는 endIndex 직전의 샘플들이며, 버퍼 길이 - 지우개 너비만큼만 사용한다.
	 */
	SweepChart.prototype.renderUpTo = function (recentSamples, endIndex) {
		var xLength = this.xLength;
		var visible = Math.min(recentSamples.length, xLength - this.eraserSize);
		var startAbs = endIndex - visible;
		var skip = recentSamples.length - visible;
		this.seriesData.forEach(function (data) {
			data.fill(null);
		});
		for (var i = 0; i < visible; i++) {
			this._writeSample((startAbs + i) % xLength, recentSamples[skip + i]);
		}
		this.chartIndex = ((endIndex % xLength) + xLength) % xLength;
		this._flush();
	};

	SweepChart.prototype.dispose = function () {
		activeCharts = activeCharts.filter(function (c) {
			return c !== this;
		}, this);
		if (this.resizeObserver) {
			this.resizeObserver.disconnect();
		}
		this.chart.dispose();
	};

	SweepChart.prototype._writeSample = function (position, sample) {
		var series = this.options.series;
		for (var s = 0; s < series.length; s++) {
			this.seriesData[s][position] = series[s].transform ? series[s].transform(sample[s]) : sample[s];
		}
	};

	/** 큐 길이에 따라 이번 프레임에 그릴 샘플 수를 정한다 - 밀려 있으면 더 많이 꺼내 따라잡는다. */
	SweepChart.prototype._calculateChunkSize = function (queueSize) {
		var pointsPerSecond = this.samplingRateHz;
		if (queueSize <= pointsPerSecond) {
			return this.targetChunkSize;
		}
		if (queueSize <= pointsPerSecond * 4) {
			return this.maxChunkSize;
		}
		return Math.min(Math.ceil(this.targetChunkSize * 2), queueSize / 10);
	};

	SweepChart.prototype._processFrame = function () {
		if (this.dataQueue.length === 0) {
			return;
		}
		var chunk = this.dataQueue.splice(0, this._calculateChunkSize(this.dataQueue.length));
		var xLength = this.xLength;
		var currIdx = this.chartIndex;

		for (var e = 0; e < this.eraserSize; e++) {
			var erasePos = (currIdx + chunk.length + e) % xLength;
			for (var s = 0; s < this.seriesData.length; s++) {
				this.seriesData[s][erasePos] = null;
			}
		}
		for (var i = 0; i < chunk.length; i++) {
			this._writeSample((currIdx + i) % xLength, chunk[i]);
		}
		this.chartIndex = (currIdx + chunk.length) % xLength;
		this._flush();
	};

	SweepChart.prototype._flush = function () {
		this.chart.setOption({
			series: this.seriesData.map(function (data) {
				return {data: data};
			})
		}, {lazyUpdate: true, silent: true});
	};

	function startLoop() {
		if (loopRunning) {
			return;
		}
		loopRunning = true;
		requestAnimationFrame(frame);
	}

	function frame(now) {
		if (activeCharts.length === 0) {
			loopRunning = false;
			return;
		}
		if (!document.hidden && now - lastFrameTime >= FRAME_INTERVAL) {
			activeCharts.forEach(function (chart) {
				chart._processFrame();
			});
			lastFrameTime = now;
		}
		requestAnimationFrame(frame);
	}

	// 탭이 숨겨져 있는 동안에는 그리지 않고 큐만 쌓이므로, 다시 보일 때 밀린 데이터를 버린다
	// (그대로 두면 한참 전 파형을 뒤늦게 따라 그리느라 실시간과 어긋난다).
	document.addEventListener("visibilitychange", function () {
		if (document.hidden) {
			return;
		}
		activeCharts.forEach(function (chart) {
			chart.dataQueue = [];
		});
	});

	/** 우측 상단 1mv(세로 1.0), 우측 하단 1sec(가로 1초) 기준 눈금 */
	function buildScaleMarkers(xLength, s, yMin, yMax) {
		// x축이 category(샘플 번호)라 좌표는 정수 인덱스여야 한다.
		function line(from, to, label) {
			from = [Math.round(from[0]), from[1]];
			to = [Math.round(to[0]), to[1]];
			var start = {coord: from, lineStyle: {color: SCALE_COLOR, width: 1, type: "solid"}};
			var end = {coord: to};
			if (label) {
				end.label = {show: true, formatter: label, fontSize: 11, color: SCALE_COLOR, distance: [0, 0]};
			}
			return [start, end];
		}
		var x = xLength;
		return [
			line([x - s, yMax], [x - s / 2, yMax]),
			line([x - s, yMax - 1], [x - s, yMax]),
			line([x - s / 2, yMax - 1], [x - s / 2, yMax]),
			line([x - s * 1.5, yMax - 1], [x - s, yMax - 1]),
			line([x - s / 2, yMax - 1], [x - 1, yMax - 1]),
			line([x - s * 2, yMax - 0.25], [x - s * 2, yMax - 0.25], "1mv"),
			line([x - s * 5, yMin + 0.1], [x - s * 5, yMin]),
			line([x - s * 5, yMin], [x - 1, yMin]),
			line([x - 1, yMin + 0.1], [x - 1, yMin]),
			line([x - s * 2.5, yMin + 0.25], [x - s * 2.5, yMin + 0.25], "1sec")
		];
	}

	// ---------- 대시보드/재생 화면이 같은 모양으로 쓰는 차트 프리셋 ----------

	// y축은 버퍼의 순간 min/max로 auto-scale하지 않고 채널별 고정 범위로 그린다
	// (auto-scale은 미세한 노이즈도 큰 변화처럼 보이게 만들고, 채널마다 스케일이 달라 비교가 어려움).
	// ECG: 시뮬레이터 기준 대략 -0.28~1.02 (R파 피크 ~1.0) -> -1.0~2.0, 임상 모니터처럼 초록색.
	var ECG_COLOR = "#19D3C5";
	var ECG_MIN = -1.0, ECG_MAX = 2.0;
	// 가속도 x/y/z는 한 차트에 겹쳐 그리므로 같은 축척을 공유한다. z축만 중력(약 9.8) 성분이
	// 실려 있어 ACCEL_Z_BASELINE만큼 빼서 x/y와 같은 "0 근방 흔들림" 값으로 맞춘다.
	// 세 선이 완전히 겹치지 않도록 값 단위로 살짝 어긋나게(lane offset) 그린다.
	var ACCEL_AXIS_COLORS = {x: "#2f5d8f", y: "#9c6485", z: "#146b4f"};
	var ACCEL_MIN = -2, ACCEL_MAX = 2;
	var ACCEL_Z_BASELINE = 9.8;
	var ACCEL_LANE_OFFSET = 0.4;

	/** ECG 차트. 샘플 한 건 = [value] */
	SweepChart.ecg = function (container, samplingRateHz, windowSeconds) {
		return new SweepChart(container, {
			samplingRateHz: samplingRateHz,
			windowSeconds: windowSeconds,
			yMin: ECG_MIN,
			yMax: ECG_MAX,
			yInterval: 0.5,
			scaleMarkers: true,
			series: [{color: ECG_COLOR}]
		});
	};

	/** 가속도 x/y/z 차트. 샘플 한 건 = [x, y, z] */
	SweepChart.acceleration = function (container, samplingRateHz, windowSeconds) {
		return new SweepChart(container, {
			samplingRateHz: samplingRateHz,
			windowSeconds: windowSeconds,
			yMin: ACCEL_MIN,
			yMax: ACCEL_MAX,
			yInterval: 0.5,
			series: [
				{color: ACCEL_AXIS_COLORS.x, transform: function (v) { return v + ACCEL_LANE_OFFSET; }},
				{color: ACCEL_AXIS_COLORS.y},
				{color: ACCEL_AXIS_COLORS.z, transform: function (v) { return v - ACCEL_Z_BASELINE - ACCEL_LANE_OFFSET; }}
			]
		});
	};

	/** 가속도 메시지의 {x, y, z} 샘플을 차트 입력 형식([x, y, z])으로 바꾼다. */
	SweepChart.toAccelRow = function (sample) {
		return [sample.x, sample.y, sample.z];
	};

	/** ECG 샘플 값을 차트 입력 형식([value])으로 바꾼다. */
	SweepChart.toEcgRow = function (value) {
		return [value];
	};

	return SweepChart;
})();
