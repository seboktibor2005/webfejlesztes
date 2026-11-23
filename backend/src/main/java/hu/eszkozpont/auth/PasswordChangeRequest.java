package hu.eszkozpont.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Add meg a jelenlegi jelszavad.")
        @Size(max = 64, message = "A jelszó legfeljebb 64 karakter lehet.") @Utf8Length String currentPassword,
        @NotBlank(message = "Add meg az új jelszavad.")
        @Size(min = 12, max = 64, message = "Az új jelszó 12–64 karakter hosszú legyen.") @Utf8Length String newPassword) {}
