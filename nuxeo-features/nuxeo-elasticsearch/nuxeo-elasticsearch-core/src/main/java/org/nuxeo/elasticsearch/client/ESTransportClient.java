/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bdelbosc
 */
package org.nuxeo.elasticsearch.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.nuxeo.elasticsearch.api.ESClient;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @since 9.3
 */
public class ESTransportClient implements ESClient {
    private static final Log log = LogFactory.getLog(ESTransportClient.class);
    private Client client;

    public ESTransportClient(Client client) {
        this.client = client;
    }

    @Override
    public Client _getClient() {
        return client;
    }

    @Override
    public boolean waitForYellowStatus(String[] indexNames, int timeoutSecond) {
        boolean ret = true;
        String timeout = timeoutSecond + "s";
        log.debug("Waiting for cluster yellow health status, indexes: " + Arrays.toString(indexNames));
        String errorMessage = null;
        try {
            ClusterHealthResponse response = client.admin()
                    .cluster()
                    .prepareHealth(indexNames)
                    .setTimeout(timeout)
                    .setWaitForYellowStatus()
                    .get();
            if (response.isTimedOut()) {
                ret = false;
                errorMessage = "ES Cluster health status not Yellow after " + timeout + " give up: "
                        + response;
            } else {
                if ((indexNames.length > 0) && response.getStatus() != ClusterHealthStatus.GREEN) {
                    log.warn("Es Cluster ready but not GREEN: " + response);
                    ret = false;
                } else {
                    log.info("ES Cluster ready: " + response);
                }
            }
        } catch (NoNodeAvailableException e) {
            errorMessage = "Failed to connect to elasticsearch, check addressList and clusterName: " + e.getMessage();
        }
        if (errorMessage != null) {
            log.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        return ret;
    }

    @Override
    public void refresh(String indexName) {
        client.admin().indices().prepareRefresh(indexName).get();
    }

    @Override
    public void flush(String indexName) {
        client.admin().indices().prepareFlush(indexName).get();
    }

    @Override
    public void optimize(String indexName) {
        client.admin().indices().prepareForceMerge(indexName).get();
    }

    @Override
    public boolean indexExists(String indexName) {
        return client.admin()
                .indices()
                .prepareExists(indexName)
                .execute()
                .actionGet()
                .isExists();
    }

    @Override
    public boolean mappingExists(String indexName, String type) {
        return client.admin().indices()
                .prepareGetMappings(indexName)
                .execute()
                .actionGet()
                .getMappings()
                .get(indexName)
                .containsKey(type);
    }

    @Override
    public void deleteIndex(String indexName, int timeoutSecond) {
        TimeValue timeout = new TimeValue(timeoutSecond, TimeUnit.SECONDS);
        client.admin().indices().delete(new DeleteIndexRequest(indexName)
                .timeout(timeout).masterNodeTimeout(timeout)).actionGet();
    }

    @Override
    public void createIndex(String indexName, String jsonSettings) {
        client.admin()
                .indices()
                .prepareCreate(indexName)
                .setSettings(jsonSettings, XContentType.JSON)
                .get();
    }

    @Override
    public void createMapping(String indexName, String type, String jsonMapping) {
        client.admin()
                .indices()
                .preparePutMapping(indexName)
                .setType(type)
                .setSource(jsonMapping, XContentType.JSON)
                .get();
    }

    @Override
    public BulkResponse bulk(BulkRequest request) {
        return client.bulk(request).actionGet();
    }

    @Override
    public DeleteResponse delete(DeleteRequest request) {
        return client.delete(request).actionGet();
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        return client.search(request).actionGet();
    }

    @Override
    public SearchResponse searchScroll(SearchScrollRequest request) {
        return client.searchScroll(request).actionGet();
    }

    @Override
    public GetResponse get(GetRequest request) {
        return client.get(request).actionGet();
    }

    @Override
    public IndexResponse index(IndexRequest request) {
        return client.index(request).actionGet();
    }

    @Override
    public ClearScrollResponse clearScroll(ClearScrollRequest request) {
        return client.clearScroll(request).actionGet();
    }

    @Override
    public void close() throws Exception {
        client.close();
        client = null;
    }
}
