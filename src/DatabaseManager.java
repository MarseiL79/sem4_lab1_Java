import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/birdsdb";
    private static final String USER = "postgres";
    private static final String PASS = "1JSuUbzVtWQh";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL Driver not found", e);
        }
    }

    public static void saveBirds(List<Bird> birds, String type) throws SQLException {
        String sql = "INSERT INTO birds " +
                "(id, type, x, y, birth_time, lifetime) " +
                "VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "x = EXCLUDED.x, y = EXCLUDED.y, type = EXCLUDED.type, " +
                "birth_time = EXCLUDED.birth_time, lifetime = EXCLUDED.lifetime";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement st = conn.prepareStatement(sql)) {
            for (Bird b : birds) {
                st.setInt(1, b.id);
                st.setString(2, type);
                st.setInt(3, b.x);
                st.setInt(4, b.y);
                st.setLong(5, b.birthTime);
                st.setLong(6, b.lifetime);
                st.addBatch();
            }
            st.executeBatch();
        }
    }

    /** Загружает из БД всех птиц данного типа */
    public static List<Bird> loadBirds(String typeFilter, Habitat habitat) throws SQLException {
        String sql = "SELECT id, x, y, birth_time, lifetime FROM birds WHERE type = ?";
        List<Bird> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, typeFilter);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int x  = rs.getInt("x");
                    int y  = rs.getInt("y");
                    long birth = rs.getLong("birth_time");
                    long life  = rs.getLong("lifetime");
                    if ("ADULT".equals(typeFilter)) {
                        list.add(new AdultBird(x, y, birth, life, id));
                    } else {
                        list.add(new Chick(x, y, birth, life, id));
                    }
                }
            }
        }
        return list;
    }
}
