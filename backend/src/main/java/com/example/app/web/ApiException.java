package com.example.app.web;

import java.util.List;
import java.util.Objects;

/**
 * 業務の異常 (E-01 ～ E-08) を表す例外。{@link ErrorHandler} がエラーの応答に変える (アーキテクチャ設計 8.3)。
 *
 * <p>入力ごとの誤り ({@link FieldError}) は E-01 のときだけ持つ。
 */
public final class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final transient List<FieldError> fieldErrors;

    /** E-01 以外の異常を作る。 */
    public ApiException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode").code(), null, false, false);
        if (errorCode == ErrorCode.E_01) {
            throw new IllegalArgumentException("E-01 は invalidInput で作ってください");
        }
        this.errorCode = errorCode;
        this.fieldErrors = List.of();
    }

    private ApiException(List<FieldError> fieldErrors) {
        super(ErrorCode.E_01.code(), null, false, false);
        this.errorCode = ErrorCode.E_01;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** 入力ごとの誤りを持つ E-01 を作る。 */
    public static ApiException invalidInput(List<FieldError> fieldErrors) {
        if (fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("E-01 には入力ごとの誤りが 1 つ以上必要です");
        }
        return new ApiException(fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 入力ごとの誤り。E-01 以外では空。 */
    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }
}
