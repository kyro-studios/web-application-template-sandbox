package com.example.app.web;

import java.util.List;
import java.util.Objects;

/**
 * 異常を表す例外。{@link ErrorHandler} が 5.2 のエラーの応答に変える。
 *
 * <p>入力ごとの誤り ({@link FieldError}) は E-01 のときだけ持つ。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    private ApiException(ErrorCode errorCode, List<FieldError> fieldErrors) {
        // メッセージには番号だけを入れる (入力の値を例外のメッセージに含めない)
        super(Objects.requireNonNull(errorCode).code());
        this.errorCode = errorCode;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** E-01 (入力の誤り) を、入力ごとの誤りの一覧とともに作る。 */
    public static ApiException invalidInput(List<FieldError> fieldErrors) {
        return new ApiException(ErrorCode.E01, fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 入力ごとの誤り。E-01 以外では空。 */
    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
