package hu.eszkozpont.auth;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank(message = "Add meg az e-mail címed.") @Email(message = "Érvényes e-mail címet adj meg.")
        @Size(max = 254, message = "Az e-mail cím túl hosszú.") String email,
        @NotBlank(message = "Add meg a jelszavad.")
        @Size(max = 64, message = "A jelszó legfeljebb 64 karakter lehet.") @Utf8Length String password) {}
