package com.solmi.vitaltrack.measurement.history;

import com.solmi.vitaltrack.measurement.MeasurementSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRecordRepository extends JpaRepository<LocationRecord, Long> {

	List<LocationRecord> findBySessionOrderByMeasuredAtAsc(MeasurementSession session);
}
