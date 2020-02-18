package su.sres.shadowserver.storage.mappers;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import su.sres.shadowserver.auth.StoredVerificationCode;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StoredVerificationCodeRowMapper implements RowMapper<StoredVerificationCode> {

  @Override
  public StoredVerificationCode map(ResultSet resultSet, StatementContext ctx) throws SQLException {
    return new StoredVerificationCode(resultSet.getString("verification_code"),
    		resultSet.getLong("timestamp"),
            resultSet.getString("push_code"),
            resultSet.getInt("lifetime"));
  }
}