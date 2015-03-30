package liquibase.sqlgenerator.core;

import liquibase.database.Database;
import liquibase.database.core.InformixDatabase;
import liquibase.database.core.MySQLDatabase;
import liquibase.datatype.DataTypeFactory;
import liquibase.exception.DatabaseException;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.core.TagDatabaseStatement;
import liquibase.statement.core.UpdateStatement;

public class TagDatabaseGenerator extends AbstractSqlGenerator<TagDatabaseStatement> {

    @Override
    public ValidationErrors validate(TagDatabaseStatement tagDatabaseStatement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        ValidationErrors validationErrors = new ValidationErrors();
        validationErrors.checkRequiredField("tag", tagDatabaseStatement.getTag());
        return validationErrors;
    }

    @Override
    public Sql[] generateSql(TagDatabaseStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        UpdateStatement updateStatement = new UpdateStatement(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), database.getDatabaseChangeLogTableName());
        updateStatement.addNewColumnValue("TAG", statement.getTag());
        String tableNameEscaped = database.escapeTableName(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), database.getDatabaseChangeLogTableName());
        String tagEscaped = DataTypeFactory.getInstance().fromObject(statement.getTag(), database).objectToSql(statement.getTag(), database);
        if (database instanceof MySQLDatabase) {
            try {
                if (database.getDatabaseMajorVersion() < 5) {
                    return new Sql[] {
                        new UnparsedSql(
                                "UPDATE " + tableNameEscaped + " C " +
                                "LEFT JOIN (" +
                                    "SELECT MAX(DATEEXECUTED) as MAXDATE " +
                                    "FROM (" +
                                        "SELECT DATEEXECUTED " +
                                        "FROM " + tableNameEscaped +
                                    ") AS X" +
                                ") AS D " +
                                "ON C.DATEEXECUTED = D.MAXDATE " +
                                "SET C.TAG = " + tagEscaped + " " +
                                "WHERE D.MAXDATE IS NOT NULL")
                    };
                }
            } catch (DatabaseException e) {
                //assume it is version 5 or greater
            }
            updateStatement.setWhereClause(
                    "DATEEXECUTED = (" +
                        "SELECT MAX(DATEEXECUTED) " +
                        "FROM (" +
                            "SELECT DATEEXECUTED " +
                            "FROM " + tableNameEscaped +
                        ") AS X" +
                    ")");
        } else if (database instanceof InformixDatabase) {
            return new Sql[] {
                    new UnparsedSql(
                            "SELECT MAX(dateexecuted) max_date " +
                            "FROM " + tableNameEscaped + " " +
                            "INTO TEMP max_date_temp WITH NO LOG"),
                    new UnparsedSql(
                            "UPDATE " + tableNameEscaped + " " +
                            "SET TAG = " + tagEscaped + " " +
                            "WHERE DATEEXECUTED = (" +
                                "SELECT max_date " +
                                "FROM max_date_temp" +
                            ");"),
                    new UnparsedSql(
                            "DROP TABLE max_date_temp;")
            };
        } else {
            updateStatement.setWhereClause(
                    "DATEEXECUTED = (" +
                        "SELECT MAX(DATEEXECUTED) " +
                        "FROM " + tableNameEscaped +
                    ")");
        }

        return SqlGeneratorFactory.getInstance().generateSql(updateStatement, database);

    }
}
