package com.solmi.vitaltrack.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectRegisterRequest(

		@NotBlank(message = "{subject.error.name.blank}")
		String name,

		@NotBlank(message = "{subject.error.species.blank}")
		@Size(max = 50, message = "{subject.error.species.size}")
		String species
) {
}
