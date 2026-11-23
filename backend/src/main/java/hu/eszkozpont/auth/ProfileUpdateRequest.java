package hu.eszkozpont.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Add meg a neved.")
        @Size(max = 100, message = "A név legfeljebb 100 karakter lehet.") String displayName) {}
