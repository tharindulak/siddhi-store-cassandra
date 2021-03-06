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
package org.wso2.extension.siddhi.store.cassandra.utils;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CassandraTableTestUtils {

    public static final String HOST = "localhost";
    public static final String KEY_SPACE = "AnalyticsFamily";
    private static final Log LOG = LogFactory.getLog(CassandraTableTestUtils.class);
    public static final String PASSWORD = "cassandra";
    public static final String TABLE_NAME = "CassandraTestTable";
    public static final String USER_NAME = "cassandra";
    private static Session session;

    public static void initializeTable() {
        dropTable();
    }

    private static void createConn() {
        //creating Cluster object
        Cluster cluster = Cluster.builder().addContactPoint(HOST).withCredentials(USER_NAME, PASSWORD).build();
        //Creating Session object
        session = cluster.connect(KEY_SPACE);
    }

    private static synchronized void dropTable() throws InvalidQueryException {
        createConn();
        String deleteQuery = "DROP TABLE " + KEY_SPACE + "." + TABLE_NAME;
        try {
            session.execute(deleteQuery);
            LOG.info("Table dropped ...");
        } catch (InvalidQueryException ex) {
            LOG.info("No configured table with the given table name");
        }
    }

    public static long getRowsInTable() {
        createConn();
        String deleteQuery = "SELECT count(*) FROM " + KEY_SPACE + "." + TABLE_NAME;
        ResultSet rs = session.execute(deleteQuery);
        return rs.one().getLong("count");
    }

}
