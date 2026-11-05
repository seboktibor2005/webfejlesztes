package hu.eszkozpont.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Add meg a neved.") @Size(max = 100, message = "A név legfeljebb 100 karakter lehet.") String displayName,
        @NotBlank(message = "Add meg az e-mail-címed.") @Email(message = "Érvényes e-mail-címet adj meg.")
        @Size(max = 254, message = "Az e-mail-cím túl hosszú.") String email,
        @NotBlank(message = "Add meg a jelszavad.")
        @Size(min = 12, max = 64, message = "A jelszó 12–64 karakter hosszú legyen.") @Utf8Length String password) {}
