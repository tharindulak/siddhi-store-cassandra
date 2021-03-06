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
package org.wso2.extension.siddhi.store.cassandra.condition;

import org.wso2.siddhi.core.util.collection.operator.CompiledCondition;

import java.util.Map;

/**
 * Implementation class of {@link CompiledCondition} corresponding to the Cassandra Event Table.
 * Maintains the conditions returned by the ExpressionVisitor as well as as a set of boolean values for inferring
 * states to be used at runtime.
 */
public class CassandraCompiledCondition implements CompiledCondition {
    private String compiledQuery;
    private Map<Integer, Object> parameters;
    private boolean readOnlyCondition;

    public CassandraCompiledCondition(String compiledQuery, Map<Integer, Object> parameters,
                                      boolean readOnlyCondition) {
        this.compiledQuery = compiledQuery;
        this.parameters = parameters;
        this.readOnlyCondition = readOnlyCondition;
    }

    @Override
    public CompiledCondition cloneCompilation(String key) {
        return new CassandraCompiledCondition(this.compiledQuery, this.parameters, this.readOnlyCondition);
    }

    public String getCompiledQuery() {
        return compiledQuery;
    }

    public String toString() {
        return getCompiledQuery();
    }

    public Map<Integer, Object> getParameters() {
        return parameters;
    }
    public boolean getReadOnlyCondition() {
        return readOnlyCondition;
    }
}
