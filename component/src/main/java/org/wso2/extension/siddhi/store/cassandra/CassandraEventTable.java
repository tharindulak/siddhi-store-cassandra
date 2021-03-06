/*
*  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.extension.siddhi.store.cassandra;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import org.apache.log4j.Logger;

import org.wso2.extension.siddhi.store.cassandra.condition.CassandraCompiledCondition;
import org.wso2.extension.siddhi.store.cassandra.condition.CassandraConditionVisitor;
import org.wso2.extension.siddhi.store.cassandra.config.CassandraStoreConfig;
import org.wso2.extension.siddhi.store.cassandra.exception.CassandraTableException;
import org.wso2.extension.siddhi.store.cassandra.iterator.CassandraIterator;
import org.wso2.extension.siddhi.store.cassandra.util.CassandraTableUtils;
import org.wso2.extension.siddhi.store.cassandra.util.Constant;
import org.wso2.extension.siddhi.store.cassandra.util.TableMeta;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.exception.CannotLoadConfigurationException;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.table.record.AbstractRecordTable;
import org.wso2.siddhi.core.table.record.ExpressionBuilder;
import org.wso2.siddhi.core.table.record.RecordIterator;
import org.wso2.siddhi.core.util.SiddhiConstants;
import org.wso2.siddhi.core.util.collection.operator.CompiledCondition;
import org.wso2.siddhi.core.util.collection.operator.CompiledExpression;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.TableDefinition;
import org.wso2.siddhi.query.api.util.AnnotationHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBException;

import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.ANNOTATION_ELEMENT_KEY_SPACE;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.ANNOTATION_ELEMENT_TABLE_NAME;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.ANNOTATION_HOST;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.ANNOTATION_PASSWORD;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.ANNOTATION_USER_NAME;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CLOSE_PARENTHESIS;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CONFIG_FILE;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CQL_AND;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CQL_FILTERING;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CQL_ID;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CQL_PRIMARY_KEY_DEF;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CQL_TEXT;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.CQL_WHERE;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.DEFAULT_KEY_SPACE;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.EQUALS;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.OPEN_PARENTHESIS;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_COLUMNS;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.
        PLACEHOLDER_COLUMNS_AND_VALUES;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_CONDITION;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_INDEX;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_INSERT_VALUES;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_KEYSPACE;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_PRIMARY_KEYS;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_QUESTION_MARKS;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_SELECT_VALUES;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.PLACEHOLDER_TABLE;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.QUESTION_MARK;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.SEPARATOR;
import static org.wso2.extension.siddhi.store.cassandra.util.CassandraEventTableConstants.WHITESPACE;
import static org.wso2.siddhi.core.util.SiddhiConstants.ANNOTATION_STORE;


/**
 * Class representing the Cassandra Event Table implementation.
 */
@Extension(
        name = "cassandra",
        namespace = "store",
        description = "This extension assigns data sources and connection instructions to event tables. It also " +
                "implements read-write operations on connected datasource.",
        parameters = {
                @Parameter(name = "cassandra.host",
                        description = "Host that is used to get connected in to the cassandra keyspace.",
                        type = {DataType.STRING},
                        defaultValue = "localhost"),
                @Parameter(name = "table.name",
                        description = "The name with which the event table should be persisted in the store. If no " +
                                "name is specified via this parameter, the event table is persisted with the same " +
                                "name as the Siddhi table.",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "The table name defined in the Siddhi Application query."),
                @Parameter(name = "keyspace",
                        description = "User need to give the keyspace that the data is persisted. " +
                                "It is ven by the keyspace parameter",
                        type = {DataType.STRING},
                        defaultValue = "'stockTable'"),
                @Parameter(name = "username",
                        description = "Through user name user can specify the relevent username " +
                                "that is used to log in to the cassandra keyspace .",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "''"),
                @Parameter(name = "password",
                        description = "Through password user can specify the relevent password " +
                                "that is used to log in to the cassandra keyspace .",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "''"),
        },
        examples = {
                @Example(
                        syntax = "define stream StockStream (symbol string, price float, volume long); \n" +
                                "@Store(type=\"cassandra\", table.name=\"StockTable\",keyspace=\"AnalyticsFamily\"," +
                                "username=\"cassandra\",password=\"cassandra\",cassandra.host=\"localhost\")" +
                                "@IndexBy(\"volume\")" +
                                "@PrimaryKey(\"symbol\")" +
                                "define table StockTable (symbol string, price float, volume long); ",
                        description = "This definition creates an event table named `StockTable` with a column " +
                                "family `StockCF` on the Cassandra instance if it does not already exist (with 3 " +
                                "attributes named `symbol`, `price`, and `volume` of the `string`, " +
                                "`float` and `long` types respectively). The connection is made as specified by the " +
                                "parameters configured for the '@Store' annotation. The `symbol` attribute is " +
                                "considered a unique field, and the values for this attribute are the " +
                                "Cassandra row IDs."
                )
        }
)
public class CassandraEventTable extends AbstractRecordTable {

    private Session session;
    private List<Attribute> schema;
    private List<Attribute> primaryKeys;
    private Annotation storeAnnotation;
    private String tableName;
    private String keyspace;
    private String host;
    private String addDataQuerySt;
    private List<Integer> objectAttributes;  // Used for object data insertion.
    private boolean noKeys;
    private String selectQuery;
    private String updateQuery;
    private Annotation indexAnnotation;
    private boolean noKeyTable;              // To check whether table does not have primary key and if it has primary
                                            // keys check the are matching with the persisted primary keys
    private Map<String, String> persistedKeyColumns; // column name -> data type
    private CassandraStoreConfig cassandraStoreConfig;

    private static final Logger LOG = Logger.getLogger(CassandraEventTable.class);

    @Override
    protected void init(TableDefinition tableDefinition, ConfigReader configReader) {
        this.schema = tableDefinition.getAttributeList();
        this.storeAnnotation = AnnotationHelper.getAnnotation(ANNOTATION_STORE, tableDefinition.getAnnotations());
        Annotation primaryKeyAnnotation = AnnotationHelper.getAnnotation(SiddhiConstants.ANNOTATION_PRIMARY_KEY,
                tableDefinition.getAnnotations());
        this.indexAnnotation = AnnotationHelper.getAnnotation(SiddhiConstants.ANNOTATION_INDEX_BY,
                tableDefinition.getAnnotations());
        String tableName = storeAnnotation.getElement(ANNOTATION_ELEMENT_TABLE_NAME);
        String keyspace = storeAnnotation.getElement(ANNOTATION_ELEMENT_KEY_SPACE);
        LOG.info("Table " + tableName + " is initialized");
        this.tableName = CassandraTableUtils.isEmpty(tableName) ? tableDefinition.getId() : tableName;
        this.host = storeAnnotation.getElement(ANNOTATION_HOST);
        this.keyspace = CassandraTableUtils.isEmpty(keyspace) ? DEFAULT_KEY_SPACE : keyspace;

        // loading cassandra config file
        try {
            this.cassandraStoreConfig = new CassandraTableUtils().readConfigFile(configReader);
        } catch (JAXBException e) {
            throw new CassandraTableException("Could not find the configuration file. Please insert the " +
                    CONFIG_FILE + " in the resources folder", e);
        } catch (CannotLoadConfigurationException e) {
            throw new CassandraTableException("Cannot find a cassandra "
                    + "configuration for the keyspace", e);
        }

        if (primaryKeyAnnotation == null) {
            this.noKeys = true;
            this.primaryKeys = new ArrayList<>();
            Attribute primaryKey = new Attribute(CQL_ID, Attribute.Type.STRING);
            this.primaryKeys.add(primaryKey);
        } else {
            this.primaryKeys = CassandraTableUtils.initPrimaryKeys(this.schema, primaryKeyAnnotation);
        }
    }

    @Override
    protected void add(List<Object[]> records) throws ConnectionUnavailableException {
        PreparedStatement preparedStatement = this.session.prepare(this.addDataQuerySt);
        for (Object record[] : records) {
            if (this.objectAttributes.size() != 0) {
                this.objectAttributes.forEach(columnNo -> {
                    Object oldData = record[columnNo];
                    try {
                        // TODO: 1/23/18 object is not supported
                        record[columnNo] = this.resolveObjectData(oldData);
                    } catch (IOException | ConnectionUnavailableException ex) {
                        throw new CassandraTableException("Error in object insertion ensure that the objects " +
                                "are serializable.", ex);
                    }
                });
            }
            this.addData(record, preparedStatement);
        }
    }

    @Override
    protected RecordIterator<Object[]> find(Map<String, Object> findConditionParameterMap,
                                            CompiledCondition compiledCondition) throws ConnectionUnavailableException {
        CassandraCompiledCondition cassandraCompiledCondition = (CassandraCompiledCondition) compiledCondition;
        String compiledQuery = cassandraCompiledCondition.getCompiledQuery();
        //This array consists of values to be passed to the prepared statement
        String finalSearchQuery;
        PreparedStatement preparedStatement;
        ResultSet result;
        if (compiledQuery.equals("")) {
            finalSearchQuery = this.selectQuery.replace(PLACEHOLDER_CONDITION, "").
                    replace(CQL_WHERE, "").replace(CQL_FILTERING, "");
            result = this.session.execute(finalSearchQuery);
        } else {
            finalSearchQuery = this.selectQuery.replace(PLACEHOLDER_CONDITION, compiledQuery);
            preparedStatement = this.session.prepare(finalSearchQuery);
            Object[] argSet = this.constructArgSet(compiledCondition, findConditionParameterMap);
            BoundStatement bound = preparedStatement.bind(argSet);
            result = this.session.execute(bound);
        }
        return new CassandraIterator(result.iterator(), this.schema);
    }

    @Override
    protected boolean contains(Map<String, Object> containsConditionParameterMap,
                               CompiledCondition compiledCondition) throws ConnectionUnavailableException {
        Object[] argSet = this.constructArgSet(compiledCondition, containsConditionParameterMap);
        String compiledQuery = ((CassandraCompiledCondition) compiledCondition).getCompiledQuery();
        String cql = this.cassandraStoreConfig.getRecordExistQuery().replace(PLACEHOLDER_KEYSPACE, this.keyspace).
                replace(PLACEHOLDER_TABLE, this.tableName).replace(PLACEHOLDER_CONDITION, compiledQuery);
        PreparedStatement preparedStatement  = this.session.prepare(cql);
        BoundStatement boundStatement = preparedStatement.bind(argSet);
        ResultSet rs = this.session.execute(boundStatement);
        return !(rs.one() == null);
    }

    @Override
    protected void update(CompiledCondition updateCondition, List<Map<String, Object>> updateConditionParameterMaps,
                          Map<String, CompiledExpression> updateSetExpressions, List<Map<String,
            Object>> updateSetParameterMaps) throws ConnectionUnavailableException {
        int i = 0;
        for (Map<String, Object> updateSetParameterMap : updateSetParameterMaps) {
            // When the user has define the primary key
            if (this.containsAllPrimaryKeys(updateConditionParameterMaps.get(i)) && !(noKeyTable) &&
                    ((CassandraCompiledCondition) updateCondition).getReadOnlyCondition()) {
                // if there is a match to the provided key is found update is possible
                if (contains(updateConditionParameterMaps.get(i), updateCondition)) {
                    this.updateSingleRow(updateSetParameterMap, updateCondition, updateConditionParameterMaps.get(i));
                } else {
                    throw new CassandraTableException("Row does not exist with the provided keys.. " +
                            "Try to update with existing keys. Update failed. ");
                }

            } else if (this.noKeyTable) {
                // need to search the rows and update them
                List<String> ids = this.findAllIDs(updateCondition, updateConditionParameterMaps.get(i));
                this.executeAsBatchNoIdUpdate(ids, updateSetParameterMap);
            } else {
                List<Object[]> ids = this.findAllUserDefinedIDs(updateCondition, updateConditionParameterMaps.get(i));
                this.executeAsBatchNonPrimeUpdate(ids, updateSetParameterMap);
            }
            i++;
        }
    }

    @Override
    protected void delete(List<Map<String, Object>> deleteConditionParameterMaps,
                          CompiledCondition compiledCondition) throws ConnectionUnavailableException {
        String deleteQuery = this.cassandraStoreConfig.getRecordDeleteQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).
                replace(PLACEHOLDER_TABLE, this.tableName).replace(PLACEHOLDER_CONDITION,
                ((CassandraCompiledCondition) compiledCondition).getCompiledQuery());

        deleteConditionParameterMaps.forEach(deleteConditionMap -> {
            if (this.containsAllPrimaryKeys(deleteConditionMap) && !(this.noKeyTable) &&
                    ((CassandraCompiledCondition) compiledCondition).getReadOnlyCondition()) {
                this.deleteSingleRow(deleteConditionMap, compiledCondition, deleteQuery);
            } else if (this.noKeyTable) {
                // need to find the key values with the column name _id
                List<String> ids = this.findAllIDs(compiledCondition, deleteConditionMap);
                this.executeAsBatchNoIdDelete(ids);
            } else {
                // need to find the key values defined by user in table defining
                List<Object[]> ids = this.findAllUserDefinedIDs(compiledCondition, deleteConditionMap);
                // Delete when user does not give the primary keys or if it is not a readonly condition
                this.executeAsBatchNonPrimeDelete(ids);
            }
        });
    }

    @Override
    protected void updateOrAdd(CompiledCondition updateCondition, List<Map<String,
            Object>> updateConditionParameterMaps, Map<String, CompiledExpression> updateSetExpressions,
                               List<Map<String, Object>> updateSetParameterMaps, List<Object[]> addingRecords)
            throws ConnectionUnavailableException {
        int i = 0;
        for (Map<String, Object> updateSetParameterMap : updateSetParameterMaps) {
            if (this.containsAllPrimaryKeys(updateConditionParameterMaps.get(i)) && !(noKeyTable) &&
                    ((CassandraCompiledCondition) updateCondition).getReadOnlyCondition()) {
                this.updateOrAddSingleRow(updateSetParameterMap, updateCondition, updateConditionParameterMaps.get(i));
            } else if (this.noKeyTable) {
                // need to search the rows and update them
                List<String> ids = this.findAllIDs(updateCondition, updateConditionParameterMaps.get(i));
                if (ids.size() == 0) {
                    // need to insert
                    this.updateOrAddToNoKeyTable(updateSetParameterMap);
                } else {
                    // need to update
                    this.executeAsBatchNoIdUpdate(ids, updateSetParameterMap);
                }
            } else {
                // updating key defined table
                List<Object[]> ids = this.findAllUserDefinedIDs(updateCondition, updateConditionParameterMaps.get(i));
                if (ids.size() == 0) {
                    // Since we do not know the primary key this operation cannot be done
                    throw new CassandraTableException("No results found for the given values. Only update " +
                            "functionality is capable without primary keys. If update or insert operation is needed" +
                            "whole key should be included");
                } else {
                    this.executeAsBatchNonPrimeUpdate(ids, updateSetParameterMap);
                }
                i++;
            }

        }
    }

    @Override
    protected CompiledCondition compileCondition(ExpressionBuilder expressionBuilder) {
        CassandraConditionVisitor visitor = new CassandraConditionVisitor();
        expressionBuilder.build(visitor);
        return new CassandraCompiledCondition(visitor.returnCondition(), visitor.getParameters(),
                visitor.getReadOnlyCondition());
    }

    @Override
    protected CompiledExpression compileSetAttribute(ExpressionBuilder expressionBuilder) {
        return compileCondition(expressionBuilder);
    }

    @Override
    protected void connect() throws ConnectionUnavailableException {
        //creating Cluster object
        String username, password;
        username = this.storeAnnotation.getElement(ANNOTATION_USER_NAME);
        password = this.storeAnnotation.getElement(ANNOTATION_PASSWORD);
        Cluster cluster = Cluster.builder().addContactPoint(host).withCredentials(username, password).build();
        // NoHostAvailableException: All host(s) tried for query failed   when the connection is not established..
        // Runtime error thrown
        this.session = cluster.connect(this.storeAnnotation.getElement(this.keyspace));
        this.checkTable();
        this.buildInsertAndSelectQuery();
    }

    @Override
    protected void disconnect() {
        if (session != null) {
            this.session.close();
        }
    }

    @Override
    protected void destroy() {
    }

    /**
     * This method is used to convert an object to a byte array to be persisted as a blob data
     * @param cellData data that is in a cell of a column
     * @return Object in a the form of bytes
     * @throws ConnectionUnavailableException exception thrown when the connection cannot be established
     * @throws IOException exception thrown in a IO operation failure
     */
    private Object resolveObjectData(Object cellData) throws ConnectionUnavailableException, IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos);
        out.writeObject(cellData);
        out.flush();
        byte[] dataToBytes = bos.toByteArray();
        bos.close();
        return ByteBuffer.wrap(dataToBytes);
    }

    /**
     * This method will be used to inset data to the cassandra keyspace.
     * @param record records that need to be added to the table, each Object[] represent a record and it will match
     * the attributes of the Table Definition passed by the add method in CassandraEventTable.
     * @param preparedStatement prepared statement to insert data
     * @throws ConnectionUnavailableException exception thrown when the connection cannot be established
     */
    private void addData(Object[] record, PreparedStatement preparedStatement) throws ConnectionUnavailableException {
        // Need to decide whether this table has a primary key. If there is a primary key
        // then the primary key annotation should n`t be null
        BoundStatement bound;
        if (this.noKeyTable) {
            List<Object> pkRecords = new ArrayList<>(Arrays.asList(record));
            pkRecords.add(CassandraTableUtils.generatePrimaryKeyValue());
            bound = preparedStatement.bind(pkRecords.toArray());
            this.session.execute(bound);
        } else {
            bound = preparedStatement.bind(record);
            this.session.execute(bound);
        }
    }

    /**
     * Find arguments to matching the compiled condition
     *
     * @param compiledCondition map of matching StreamVariable Ids and their values
     *                                  corresponding to the compiled condition
     * @param conditionParameterMap         the compiledCondition against which records should be matched
     * @return Object[] of matching arguments
     */
    private Object[] constructArgSet(CompiledCondition compiledCondition,
                                     Map<String, Object> conditionParameterMap) {
        CassandraCompiledCondition cassandraCompiledCondition = (CassandraCompiledCondition) compiledCondition;
        Map<Integer, Object> compiledParameters = cassandraCompiledCondition.getParameters();
        Object[] argSet = new Object[compiledParameters.size()];
        int i = 0;
        for (SortedMap.Entry<Integer, Object> entry : compiledParameters.entrySet()) {
            Object parameter = entry.getValue();
            if (parameter instanceof Constant) {
                // if the value is a constant
                Constant constant = (Constant) parameter;
                argSet[i] = constant.getValue();
            } else {
                // if the value is an attribute
                Attribute variable = (Attribute) parameter;
                String attributeName = variable.getName();
                argSet[i] = conditionParameterMap.get(attributeName);
            }
            i++;
        }
        return argSet;
    }

    /**
     * This used to find all ids (Primary Key) to update or delete a row in case where
     * user has not defined the primary key in the data insertion state.
     * @param compiledCondition  the compiledCondition against which records should be matched
     * @param conditionParameterMap the compiledCondition against which records should be matched
     */
    private List<String> findAllIDs(CompiledCondition compiledCondition,
                                         Map<String, Object> conditionParameterMap) {
        String compiledQuery = ((CassandraCompiledCondition) compiledCondition).getCompiledQuery();
        String finalSearchQuery = this.cassandraStoreConfig.getRecordSelectNoKeyTable().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName).
                replace(PLACEHOLDER_CONDITION, compiledQuery);
        PreparedStatement preparedStatement = this.session.prepare(finalSearchQuery);
        Object[] argSet = this.constructArgSet(compiledCondition, conditionParameterMap);
        BoundStatement bound = preparedStatement.bind(argSet);
        List<String> ids = new ArrayList<>();
        ResultSet result = this.session.execute(bound);
        result.forEach(row -> ids.add(row.getString(CQL_ID)));
        return ids;
    }

    /**
     * This used to find all ids (Primary Key) to update or delete a row in case where
     * user has not defined the primary key in the the condition.
     * @param compiledCondition  the compiledCondition against which records should be matched
     * @param conditionParameterMap the compiledCondition against which records should be matched
     */
    private List<Object[]> findAllUserDefinedIDs(CompiledCondition compiledCondition,
                                                      Map<String, Object> conditionParameterMap) {
        // constructs the values to be extracted fro the relevent table
        // eg - select val1,val2,val3
        StringBuilder keyValueSelector = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> persistedColumn : persistedKeyColumns.entrySet()) {
            keyValueSelector.append(persistedColumn.getKey());
            if (i != this.persistedKeyColumns.size() - 1) {
                keyValueSelector.append(SEPARATOR);
            }
            i++;
        }
        String compiledQuery = ((CassandraCompiledCondition) compiledCondition).getCompiledQuery();
        String finalSearchQuery = this.cassandraStoreConfig.getRecordSelectQuery().
                replace(PLACEHOLDER_SELECT_VALUES, keyValueSelector.toString()).
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName).
                replace(PLACEHOLDER_CONDITION, compiledQuery);
        PreparedStatement preparedStatement = this.session.prepare(finalSearchQuery);
        Object[] argSet = this.constructArgSet(compiledCondition, conditionParameterMap);
        BoundStatement bound = preparedStatement.bind(argSet);
        List<Object[]> ids = new ArrayList<>();
        ResultSet result = session.execute(bound);
        for (Row row : result) {
            Object[] rowKey = new Object[this.persistedKeyColumns.size()];
            int rowNo = 0;
            for (Map.Entry<String, String> persistedColumn : this.persistedKeyColumns.entrySet()) {
                rowKey[rowNo++] = row.getObject(persistedColumn.getKey());
            }
            ids.add(rowKey);
        }
        return ids;
    }

    /**
     * This creates the prepared statement that is used in a table where a
     * primary key is not defined by the user as a batch and also the batch is executed.
     * @param ids set of ids found to perform the operation
     */
    private void executeAsBatchNoIdDelete(List<String> ids) {
        BatchStatement batchStatement = new BatchStatement();
        String condition = CQL_ID + EQUALS + QUESTION_MARK;
        String deleteQuery = this.cassandraStoreConfig.getRecordDeleteQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).
                replace(PLACEHOLDER_TABLE, this.tableName).replace(PLACEHOLDER_CONDITION, condition);
        PreparedStatement preparedStatement = session.prepare(deleteQuery);
        ids.forEach(id -> {
            BoundStatement boundStatement = new BoundStatement(preparedStatement);
            boundStatement.bind(id);
            batchStatement.add(boundStatement);
        });
        this.session.execute(batchStatement);
    }

    /**
     * This creates the prepared statement (to delete the table) that is used in a table where a
     * primary key is not defined at the condition by the user as a batch and also the batch is executed.
     * @param ids set of ids found to perform the operation
     */
    private void executeAsBatchNonPrimeDelete(List<Object[]> ids) {
        int i = 0;
        StringBuilder condition = new StringBuilder();
        // building the condition statement
        for (Map.Entry<String, String> persistedColumn : this.persistedKeyColumns.entrySet()) {
            condition.append(persistedColumn.getKey()).append(EQUALS).append(QUESTION_MARK);
            if (i != this.persistedKeyColumns.size() - 1) {
                condition.append(WHITESPACE).append(CQL_AND).append(WHITESPACE);
            }
            i++;
        }
        String deleteQuery = this.cassandraStoreConfig.getRecordDeleteQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName).
                replace(PLACEHOLDER_CONDITION, condition.toString());
        BatchStatement batchStatement = new BatchStatement();
        PreparedStatement preparedStatement = this.session.prepare(deleteQuery);
        ids.forEach(id -> {
            BoundStatement boundStatement = new BoundStatement(preparedStatement);
            boundStatement.bind(id);
            batchStatement.add(boundStatement);
        });
        this.session.execute(batchStatement);
    }

    /**
     * This creates the prepared statement (to update the table) that is used in a table where a
     * primary key is not defined at the condition by the user as a batch and also the batch is executed.
     * @param ids set of ids found to perform the operation
     * @param updateParameterMap set parameters used to update the table
     */
    private void executeAsBatchNonPrimeUpdate(List<Object[]> ids, Map<String, Object> updateParameterMap) {
        // keys that are in the condition as well as the set parameters
        // these should be removed
        List<String> keys = new ArrayList<>();
        // TODO: 1/24/18 add java 8 lamda
        updateParameterMap.forEach((parameter, value) -> {
            if (this.persistedKeyColumns.containsKey(parameter)) {
                keys.add(parameter);
            }
        });
        //Since cassandra cannot update a primary key column we need to remove the primary key values sent
        keys.forEach(updateParameterMap :: remove);

        BatchStatement batchStatement = new BatchStatement();
        List<Object> setValues = this.buildUpdateStatement(updateParameterMap);
        PreparedStatement preparedStatement = this.session.prepare(this.updateQuery);

        ids.forEach(id -> {
            BoundStatement boundStatement = new BoundStatement(preparedStatement);
            List<Object> recordCells = new ArrayList<>();
            recordCells.addAll(setValues);
            recordCells.addAll(new ArrayList<>(Arrays.asList(id)));
            boundStatement.bind(recordCells.toArray());
            batchStatement.add(boundStatement);
        });
        this.session.execute(batchStatement);
    }

    /**
     * This creates the prepared statement that is used in a table where a
     * primary key is not defined by the user as a batch and also the batch is executed.
     * @param ids set of ids found to perform the operation
     * @param updateParameterMap set parameters used to update the table
     */
    private void executeAsBatchNoIdUpdate(List<String> ids, Map<String, Object> updateParameterMap) {
        BatchStatement batchStatement = new BatchStatement();
        List<Object> setValues = this.buildCQLUpdateSetStatement(updateParameterMap);
        String condition = CQL_ID + EQUALS + QUESTION_MARK;
        String finalUpdateQuery = this.updateQuery.replace(PLACEHOLDER_CONDITION, condition);
        PreparedStatement preparedStatement = session.prepare(finalUpdateQuery);
        ids.forEach(id -> {
            BoundStatement boundStatement = new BoundStatement(preparedStatement);
            List<Object> recordCells = new ArrayList<>();
            recordCells.addAll(setValues);
            recordCells.add(id);
            boundStatement.bind(recordCells.toArray());
            batchStatement.add(boundStatement);
        });
        this.session.execute(batchStatement);
    }

    /**
     * This is used to delete a single row entry that is matched with the primary key
     * @param deleteConditionParameterMap set parameters used to delete a row the table
     * @param compiledCondition the compiledCondition against which records should be matched
     */
    private void deleteSingleRow(Map<String, Object> deleteConditionParameterMap,
                                 CompiledCondition compiledCondition, String deleteQuery) {
        Object argSet[] = this.constructArgSet(compiledCondition, deleteConditionParameterMap);
        //InvalidQueryException may take place if the defined if the condition doeas not have keys :Throw exception.
        PreparedStatement preparedStatement = this.session.prepare(deleteQuery);
        BoundStatement bound = preparedStatement.bind(argSet);
        this.session.execute(bound);
    }

    /**
     * This used to update a single row when the exact row key is provided
     * @param updateSetParameterMap set parameters used to update the table
     * @param compiledCondition the compiledCondition against which records should be matched
     * @param updateConditionParameterMaps map of condition parameters
     */
    private void updateSingleRow(Map<String, Object> updateSetParameterMap, CompiledCondition compiledCondition,
                                 Map<String, Object> updateConditionParameterMaps) {
        List<String> keys = new ArrayList<>();
        String compiledQuery = ((CassandraCompiledCondition) compiledCondition).getCompiledQuery();

        updateSetParameterMap.forEach((parameter, value) -> {
            if (this.persistedKeyColumns.containsKey(parameter)) {
                keys.add(parameter);
            }
        });
        //Since cassandra cannot update a primary key column we need to remove the primary key values sent
        keys.forEach(updateSetParameterMap :: remove);

        List<Object> allValues = this.buildCQLUpdateSetStatement(updateSetParameterMap);
        String finalUpdateQuery = this.updateQuery.replace(PLACEHOLDER_CONDITION, compiledQuery);
        Object[] argSet = this.constructArgSet(compiledCondition, updateConditionParameterMaps);
        allValues.addAll(new ArrayList<>(Arrays.asList(argSet)));
        PreparedStatement preparedStatement = this.session.prepare(finalUpdateQuery);
        BoundStatement bound = preparedStatement.bind(allValues.toArray());
        this.session.execute(bound);
    }

    /**
     * This used to construct the CQL query to a given set parameters
     * @param updateParameterMap set parameters used to update the table
     */
    private List<Object> buildCQLUpdateSetStatement(Map<String, Object> updateParameterMap) {
        String updateCql = this.cassandraStoreConfig.getRecordUpdateQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName);
        List<Object> setValues = new ArrayList<>();
        String updateParameterString = this.buildUpdateParameterValues(updateParameterMap, setValues);
        this.updateQuery = updateCql.replace(PLACEHOLDER_COLUMNS_AND_VALUES, updateParameterString);
        return setValues;
    }

    /**
     * This used to update a single row when the exact row key is provided
     *
     * @param updateParameterMap set parameters used to update the table
     * @param setValues a list of set values that is used to construct the update query
     * @return returns the update parameter string
     */
    private String buildUpdateParameterValues(Map<String, Object> updateParameterMap, List<Object> setValues) {
        int i = 1;
        StringBuilder updateParameterString = new StringBuilder();
        int size = updateParameterMap.size();
        for (Map.Entry<String, Object> parameter : updateParameterMap.entrySet()) {
            updateParameterString.append(parameter.getKey()).append(EQUALS).append(QUESTION_MARK);
            setValues.add(parameter.getValue());
            if (i != size) {
                updateParameterString.append(SEPARATOR);
            }
            i++;
        }
        return updateParameterString.toString();
    }

    /**
     * This is used to build the update set statement
     *
     * @param updateParameterMap set parameters used to update the table
     */
    private List<Object> buildUpdateStatement(Map<String, Object> updateParameterMap) {
        // builds the update statement with set values
        List<Object> setValues = this.buildCQLUpdateSetStatement(updateParameterMap);
        StringBuilder condition = new StringBuilder();
        int size = this.persistedKeyColumns.size();
        int i = 1;
        // builds the condition
        for (Map.Entry<String, String> column : this.persistedKeyColumns.entrySet()) {
            condition.append(column.getKey()).append(EQUALS).append(QUESTION_MARK);
            if (i != size) {
                condition.append(WHITESPACE).append(CQL_AND).append(WHITESPACE);
            }
            i++;
        }
        this.updateQuery = this.updateQuery.replace(PLACEHOLDER_CONDITION, condition);
        return setValues;
    }

    /**
     * This is used to updateOrAdd a row in table where primary key is not defined
     *
     * @param updateSetParameterMap set parameters used to update the table
     */
    private void updateOrAddToNoKeyTable(Map<String, Object> updateSetParameterMap) {
        List<Object> setValues = new ArrayList<>();
        String updateParameterString = this.buildUpdateParameterValues(updateSetParameterMap, setValues);
        String condition = CQL_ID + EQUALS + QUESTION_MARK;
        String updateQuery = this.cassandraStoreConfig.getRecordUpdateQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName).
                replace(PLACEHOLDER_COLUMNS_AND_VALUES, updateParameterString).
                replace(PLACEHOLDER_CONDITION, condition);
        setValues.add(CassandraTableUtils.generatePrimaryKeyValue());
        PreparedStatement preparedStatement = this.session.prepare(updateQuery);
        BoundStatement bound = preparedStatement.bind(setValues.toArray());
        this.session.execute(bound);
    }

    /**
     * This will call updateSingleRow (updating logic) used in update method
     *
     * @param updateSetParameterMap set parameters used to update the table
     * @param compiledCondition the compiledCondition against which records should be matched
     * @param updateConditionParameterMaps map of condition parameters
     */
    private void updateOrAddSingleRow(Map<String, Object> updateSetParameterMap, CompiledCondition compiledCondition,
                                      Map<String, Object> updateConditionParameterMaps) {
        this.updateSingleRow(updateSetParameterMap, compiledCondition, updateConditionParameterMaps);
    }

    /**
     * This will check whether a certain condition contains all the primary keys in the table
     * @param paramList condition map
     * @return returns true if the condition condition contains all the primary keys
     */
    private boolean containsAllPrimaryKeys(Map<String, Object> paramList) {
        // null checking
        List<String> paramKeys = new ArrayList<>(paramList.keySet());
        if (this.persistedKeyColumns == null) {
            return false;
        } else if (paramKeys.size() != this.persistedKeyColumns.size()) {
            return false;
        }
        for (Map.Entry<String, String> persistedColumn : this.persistedKeyColumns.entrySet()) {
            if (!paramKeys.contains(persistedColumn.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * This function is used in generating the value statement and the question marks to be used in prepared
        statement and detecting object attributes
     */
    private void buildInsertAndSelectQuery() {
        int i = 0;
        StringBuilder insertValStatement = new StringBuilder();
        StringBuilder questionMarks = new StringBuilder();
        // keeping the indexes of object attributes
        this.objectAttributes = new ArrayList<>();
        for (Attribute a : schema) {
            insertValStatement.append(a.getName());
            questionMarks.append(QUESTION_MARK);
            //building the insert value statement
            if (i != schema.size() - 1) {
                insertValStatement.append(SEPARATOR);
                questionMarks.append(SEPARATOR);
            }
            //keeping the object attribute indexes in a separate array
            if (schema.get(i).getType() == Attribute.Type.OBJECT) {
                this.objectAttributes.add(i);
            }
            i++;
        }
        //initialising the select value statement
        String selectValStatement = insertValStatement.toString();
        if (this.noKeys) {
            questionMarks.append(SEPARATOR).append(QUESTION_MARK);
            insertValStatement.append(SEPARATOR).append(CQL_ID);
        }
        //query initialization to add data to the cassandra keyspace
        this.addDataQuerySt = this.cassandraStoreConfig.getRecordInsertQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).
                replace(PLACEHOLDER_TABLE, this.tableName).replace(PLACEHOLDER_INSERT_VALUES, insertValStatement).
                replace(PLACEHOLDER_QUESTION_MARKS, questionMarks);
        //Query to search data
        this.selectQuery = this.cassandraStoreConfig.getRecordSelectQuery().
                replace(PLACEHOLDER_SELECT_VALUES, selectValStatement).
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName);
    }

    /**
     * This will check whether the table is created if not will create the table
     */
    private void checkTable() {
        String checkStatement = this.cassandraStoreConfig.getTableCheckQuery().replace(PLACEHOLDER_KEYSPACE,
                this.keyspace.toLowerCase(Locale.ENGLISH)).replace(PLACEHOLDER_TABLE,
                this.tableName.toLowerCase(Locale.ENGLISH));
        ResultSet result = this.session.execute(checkStatement);

        if (this.isValidKeyspaceAndTable()) {
            if (result.one() == null) {
                this.createTable();
            } else if (!this.isTableWithDefinedColumns()) {
                throw new CassandraTableException("Problem with the table definition or key. " +
                        "Please re check the table schema and try again.");
            }
            //Otherwise table is already created.
        } else {
            throw new CassandraTableException("Invalid table name or Keyspace name. " +
                    "Please refer the cassandra documentation for naming valid table and keyspace");
        }
    }

    /**
     * This will check whether the table is created if not will create the table
     */
    private void createTable() {
        StringBuilder primaryKeyStatement = new StringBuilder();
        primaryKeyStatement.append(CQL_PRIMARY_KEY_DEF).append(OPEN_PARENTHESIS);
        int i = 0;
        for (Attribute primaryKey : this.primaryKeys) {
            primaryKeyStatement.append(primaryKey.getName());
            if (i != this.primaryKeys.size() - 1) {
                primaryKeyStatement.append(SEPARATOR);
            } else {
                primaryKeyStatement.append(CLOSE_PARENTHESIS);
            }
            i++;
        }

        StringBuilder attributeStatement = new StringBuilder();
        this.schema.forEach(attribute -> {
            attributeStatement.append(attribute.getName());
            attributeStatement.append(WHITESPACE);
            String type = CassandraTableUtils.dataConversionToCassandra(attribute.getType());
            attributeStatement.append(type);
            attributeStatement.append(SEPARATOR);
        });
        //when primary key is not given
        if (this.noKeys) {
            attributeStatement.append(CQL_ID).append(WHITESPACE).append(CQL_TEXT).append(WHITESPACE).append(SEPARATOR);
            this.noKeyTable = true;
        }

        String createStatement = this.cassandraStoreConfig.getTableCreateQuery().
                replace(PLACEHOLDER_KEYSPACE, this.keyspace).
                replace(PLACEHOLDER_TABLE, this.tableName).replace(PLACEHOLDER_COLUMNS, attributeStatement).
                replace(PLACEHOLDER_PRIMARY_KEYS, primaryKeyStatement);
        this.session.execute(createStatement);
        if (this.indexAnnotation != null) {
            this.initIndexQuery();
        }
        this.findPersistedKeys();
    }

    /**
     * User defined keys that are actually defined in the keyspace
     */
    private void findPersistedKeys() {
        String checkStatement = this.cassandraStoreConfig.getTableValidityQuery().replace(PLACEHOLDER_KEYSPACE,
                this.keyspace.toLowerCase(Locale.ENGLISH)).
                replace(PLACEHOLDER_TABLE, this.tableName.toLowerCase(Locale.ENGLISH));
        ResultSet result = this.session.execute(checkStatement);
        List<TableMeta> tableColumns = new ArrayList<>();

        for (Row row : result) {
            TableMeta tableMeta = new TableMeta(row.getString("column_name"),
                    row.getString("kind"), row.getString("type"));
            tableColumns.add(tableMeta);
        }

        this.persistedKeyColumns = new HashMap<>();
        tableColumns.stream().filter(column -> column.getKeyType().equals("partition_key") ||
                column.getKeyType().equals("clustering"))
                .forEach(column -> this.persistedKeyColumns.put(column.getColumnName(), column.getDataType()));
    }

    /**
     * This will check whether the table and keyspace is valid before creating the table
     */
    private boolean isValidKeyspaceAndTable() {
        String pattern = "^[A-Za-z0-9_]*$";
        Pattern r = Pattern.compile(pattern);
        Matcher kp = r.matcher(this.keyspace);
        Matcher table = r.matcher(this.tableName);
        return (kp.find() && table.find());
    }


    /**
     * This will check whether defined column already exists in the table and the defined primary keys
     * are already as the previously defined ones
     */
    // TODO: 1/23/18 check with this function
    private boolean isTableWithDefinedColumns() {
        String checkStatement = this.cassandraStoreConfig.getTableValidityQuery().replace(PLACEHOLDER_KEYSPACE,
                this.keyspace.toLowerCase(Locale.ENGLISH)).
                replace(PLACEHOLDER_TABLE, this.tableName.toLowerCase(Locale.ENGLISH));
        ResultSet result = this.session.execute(checkStatement);
        Map<String, String> tableDet = new HashMap<>();
        List<TableMeta> tableColumns = new ArrayList<>();
        for (Row row : result) {
            tableDet.put(row.getString("column_name"), row.getString("kind"));
            TableMeta tableMeta = new TableMeta(row.getString("column_name"),
                    row.getString("kind"), row.getString("type"));
            tableColumns.add(tableMeta);
        }

        this.persistedKeyColumns = new HashMap<>();
        Map<String, String> persistedColumns = new HashMap<>();
        for (TableMeta column : tableColumns) {
            if (column.getKeyType().equals("partition_key") || column.getKeyType().equals("clustering")) {
                this.persistedKeyColumns.put(column.getColumnName(), column.getDataType());
            }
            persistedColumns.put(column.getColumnName(), column.getDataType());
        }

        boolean validKeys = false, validColumns, validDataTypes;
        // To Check whether the column names match with the persisted column names
        for (Attribute attribute : this.schema) {
            validColumns = tableDet.containsKey(attribute.getName().toLowerCase(Locale.ENGLISH));
            if (!validColumns) {
                return false;
            }
        }

        // To Check whether the column data types match with the persisted column data types names
        for (Attribute attribute : this.schema) {
            String persistedColumnType = persistedColumns.get(attribute.getName().toLowerCase(Locale.ENGLISH));
            String inComingDataType = CassandraTableUtils.dataConversionToCassandra(attribute.getType());
            validDataTypes = persistedColumnType.equalsIgnoreCase(inComingDataType);
            if (!validDataTypes) {
                return false;
            }
        }

        if (this.noKeys) {
            validKeys = tableDet.containsKey("_id");
            this.noKeyTable = validKeys;
        } else {
            for (Attribute attribute : this.primaryKeys) {
                String pk = attribute.getName().toLowerCase(Locale.ENGLISH);
                validKeys = (tableDet.containsKey(pk) &&
                        (tableDet.get(pk).equals("partition_key") || tableDet.get(pk).equals("clustering")));
                if (!validKeys) {
                    return false;
                }
            }
        }
        return validKeys;
    }

    /**
     * This check the indexAnnotation and create indexes on the given columns
     */
    private void initIndexQuery() {
        String indexes[] = this.indexAnnotation.getElements().get(0).getValue().split(SEPARATOR);
        /*for (String index : indexes) {
            for (Attribute attribute : this.schema) {
                if (attribute.getName().trim().equals(index)) {
                    String indexQuery = this.cassandraStoreConfig.getIndexQuery().
                            replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName).
                            replace(PLACEHOLDER_INDEX, index);
                    this.session.execute(indexQuery);
                }
            }
        }*/
        Arrays.stream(indexes).forEach(index -> {
            this.schema.stream()
                    .filter(attribute -> attribute.getName().trim().equals(index))
                    .forEach(attribute -> {
                        String indexQuery = this.cassandraStoreConfig.getIndexQuery().
                                replace(PLACEHOLDER_KEYSPACE, this.keyspace).replace(PLACEHOLDER_TABLE, this.tableName).
                                replace(PLACEHOLDER_INDEX, index);
                        this.session.execute(indexQuery);
                    });
        });

    }
}
