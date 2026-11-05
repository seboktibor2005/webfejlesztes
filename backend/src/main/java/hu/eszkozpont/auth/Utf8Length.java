package hu.eszkozpont.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Utf8LengthValidator.class)
public @interface Utf8Length {
    String message() default "A jelszó UTF-8 kódolással legfeljebb 72 bájt lehet.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
