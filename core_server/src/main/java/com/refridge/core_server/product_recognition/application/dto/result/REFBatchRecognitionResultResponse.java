package com.refridge.core_server.product_recognition.application.dto.result;

import java.util.List;

public record REFBatchRecognitionResultResponse(
        int totalCount,
        int successCount,
        int rejectedCount,
        int failedCount,
        long elapsedMillis,
        List<REFRecognitionResultResponse> results
) {

    public static REFBatchRecognitionResultResponse of(
            List<REFRecognitionResultResponse> results, long elapsedMillis) {
        int success = (int) results.stream()
                .filter(r -> r.success() && !r.rejected()).count();
        int rejected = (int) results.stream()
                .filter(REFRecognitionResultResponse::rejected).count();
        int failed = results.size() - success - rejected;

        return new REFBatchRecognitionResultResponse(
                results.size(), success, rejected, failed,
                elapsedMillis, results
        );
    }

}
