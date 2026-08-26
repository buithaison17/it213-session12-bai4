package com.example.bai4;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafeSqlValidator {
    private static final int MAX_LIMIT = 100;

    // Danh sách từ khóa cấm (dùng boundary \b để tránh bắt nhầm substring trong tên cột)
    private static final String[] FORBIDDEN_KEYWORDS = {
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER",
            "TRUNCATE", "GRANT", "REVOKE", "EXEC"
    };

    // Danh sách ký tự đặc biệt nguy hiểm (Comment, Dấu chấm phẩy ngăn lệnh)
    private static final String[] DANGEROUS_PATTERNS = {
            "--", "/*", "*/", ";"
    };

    // Regex tìm mệnh đề LIMIT <số> ở cuối chuỗi (không phân biệt hoa thường)
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\s*$");

    public static String validateAndSanitize(String rawSql) {
        if (rawSql == null || rawSql.trim().isEmpty()) {
            throw new SecurityException("Câu lệnh SQL không được để trống.");
        }
        String trimmedSql = rawSql.trim();
        // Quy tắc 1: Bắt buộc bắt đầu bằng SELECT
        if (!trimmedSql.matches("(?i)^SELECT\\b.*")) {
            throw new SecurityException("Chỉ cho phép thực thi câu lệnh SELECT tra cứu dữ liệu.");
        }
        // Quy tắc 2: Kiểm tra ký tự nguy hiểm (--, /*, */, ;)
        for (String pattern : DANGEROUS_PATTERNS) {
            if (trimmedSql.contains(pattern)) {
                throw new SecurityException("Phát hiện từ khóa SQL nguy hiểm bị cấm: " + pattern);
            }
        }

        // Quy tắc 2: Kiểm tra từ khóa phá hoại
        for (String keyword : FORBIDDEN_KEYWORDS) {
            Pattern keywordPattern = Pattern.compile("(?i)\\b" + Pattern.quote(keyword) + "\\b");
            if (keywordPattern.matcher(trimmedSql).find()) {
                throw new SecurityException("Phát hiện từ khóa SQL nguy hiểm bị cấm: " + keyword);
            }
        }

        // Quy tắc 3: Cưỡng chế LIMIT
        Matcher limitMatcher = LIMIT_PATTERN.matcher(trimmedSql);

        if (limitMatcher.find()) {
            int currentLimit;
            try {
                currentLimit = Integer.parseInt(limitMatcher.group(1));
            } catch (NumberFormatException e) {
                // Trường hợp số vượt quá Integer.MAX_VALUE thì ép về MAX_LIMIT
                currentLimit = Integer.MAX_VALUE;
            }

            if (currentLimit > MAX_LIMIT) {
                // Thay thế LIMIT cũ bằng LIMIT 100
                return limitMatcher.replaceFirst("LIMIT " + MAX_LIMIT);
            }
            return trimmedSql;
        } else {
            // Chưa có LIMIT -> Nối thêm vào cuối
            return trimmedSql + " LIMIT " + MAX_LIMIT;
        }
    }
}
