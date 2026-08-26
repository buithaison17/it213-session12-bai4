package com.example.bai4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class SafeSqlValidatorTest {
    @Test
    @DisplayName("Input 1: Tự động thêm LIMIT 100 khi câu lệnh chưa có LIMIT")
    void testAddLimitWhenMissing() {
        String input = "SELECT id, tracking_code, status FROM deliveries WHERE status = 'DELAYED'";
        String expected = "SELECT id, tracking_code, status FROM deliveries WHERE status = 'DELAYED' LIMIT 100";

        String actual = SafeSqlValidator.validateAndSanitize(input);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Input 2: Giữ nguyên khi LIMIT <= 100")
    void testKeepLimitWhenUnderOrEqual100() {
        String input = "select count(*) from deliveries limit 20";
        String expected = "select count(*) from deliveries limit 20";

        String actual = SafeSqlValidator.validateAndSanitize(input);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Input 3: Ép về LIMIT 100 khi LIMIT > 100")
    void testClampLimitWhenExceeding100() {
        String input = "SELECT * FROM deliveries LIMIT 5000";
        String expected = "SELECT * FROM deliveries LIMIT 100";

        String actual = SafeSqlValidator.validateAndSanitize(input);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Input 4 (Độc hại): Ném SecurityException khi không bắt đầu bằng SELECT")
    void testBlockNonSelectQuery() {
        String input = "DROP TABLE deliveries;";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            SafeSqlValidator.validateAndSanitize(input);
        });

        assertTrue(exception.getMessage().contains("Chỉ cho phép thực thi câu lệnh SELECT tra cứu dữ liệu."));
    }

    @Test
    @DisplayName("Input 5 (Lách luật): Ném SecurityException khi chứa ký tự comment (--)")
    void testBlockSqlComment() {
        String input = "SELECT * FROM deliveries WHERE 1=1 -- delete from deliveries";

        SecurityException exception = assertThrows(SecurityException.class, () -> {
            SafeSqlValidator.validateAndSanitize(input);
        });

        assertTrue(exception.getMessage().contains("Phát hiện từ khóa SQL nguy hiểm bị cấm"));
    }

    // ==========================================
    // CÁC TEST CASES MỞ RỘNG (EDGE CASES)
    // ==========================================

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM deliveries; SELECT * FROM users", // Ký tự phân tách đa lệnh ';'
            "SELECT * FROM deliveries /* inline comment */ WHERE id = 1", // Comment dạng block '/*'
            "SELECT * FROM deliveries WHERE id = 1 AND (DELETE FROM logs)", // Từ khóa DELETE lồng
            "SELECT * FROM deliveries WHERE 1=1; DROP TABLE deliveries" // Đa lệnh kèm DROP
    })
    @DisplayName("Mở rộng: Chặn tất cả các dạng ký tự và từ khóa cấm nguy hiểm")
    void testBlockOtherDangerousPatterns(String dangerousSql) {
        assertThrows(SecurityException.class, () -> {
            SafeSqlValidator.validateAndSanitize(dangerousSql);
        });
    }

    @Test
    @DisplayName("Mở rộng: Báo lỗi khi chuỗi đầu vào rỗng hoặc chỉ có khoảng trắng")
    void testNullOrEmptyQuery() {
        assertThrows(SecurityException.class, () -> SafeSqlValidator.validateAndSanitize(null));
        assertThrows(SecurityException.class, () -> SafeSqlValidator.validateAndSanitize("   "));
    }
}
