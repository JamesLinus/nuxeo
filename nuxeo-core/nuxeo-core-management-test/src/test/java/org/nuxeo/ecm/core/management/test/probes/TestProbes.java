/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.core.management.test.probes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.management.api.ProbeInfo;
import org.nuxeo.ecm.core.management.api.ProbeManager;
import org.nuxeo.ecm.core.management.probes.AdministrativeStatusProbe;
import org.nuxeo.ecm.core.management.statuses.HealthCheckResult;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.runtime.management", //
        "org.nuxeo.ecm.core.management", //
        "org.nuxeo.ecm.core.management.test" })
public class TestProbes {

    @Inject
    CoreSession session;

    @Inject
    ProbeManager pm;

    @Test
    public void testServiceLookup() {
        assertNotNull(pm);
    }

    @Test
    public void testService() {

        pm.runAllProbes();

        ProbeInfo info = pm.getProbeInfo(AdministrativeStatusProbe.class);
        assertNotNull(info);

        info = pm.getProbeInfo("administrativeStatus");
        assertNotNull(info);

        Collection<String> names = pm.getProbeNames();
        assertTrue("admin status shortcut not listed", names.contains("administrativeStatus"));
        assertNotNull("admin status probe not published", info.getQualifiedName());

        assertEquals(1, info.getRunnedCount());
        assertFalse("not a success", info.isInError());
        assertFalse("wrong success value", info.getStatus().getAsString().equals("[unavailable]"));
        assertEquals("wrong default value", "[unavailable]", info.getLastFailureStatus().getAsString());

    }

    @Test
    public void testHealthCheck() throws IOException {

        Collection<ProbeInfo> healthCheckProbes = pm.getHealthCheckProbes();
        assertEquals(2, healthCheckProbes.size());

        HealthCheckResult result = pm.getOrRunHealthChecks();
        assertTrue(result.isHealthy());
        assertEquals("{\"runtimeStatus\":\"ok\",\"repositoryStatus\":\"ok\"}", result.toJson());

    }

    @Test
    public void testSingleProbeStatus() throws IOException {

        HealthCheckResult result = pm.getOrRunHealthCheck("runtimeStatus");
        ProbeInfo probeInfo = pm.getProbeInfo("runtimeStatus");

        assertTrue(result.isHealthy());
        assertTrue(probeInfo.getStatus().isSuccess());
        assertEquals("{\"runtimeStatus\":\"ok\"}", result.toJson());
    }
    
    @Test
    public void testInvalidProbe() {
        HealthCheckResult result = pm.getOrRunHealthCheck("invalidProbe");
        assertTrue(result.isHealthy());
        assertEquals("", result.toJson());
    }

    @Test
    public void testRepositoryStatusProbe() throws IOException {

        HealthCheckResult result = pm.getOrRunHealthCheck("repositoryStatus");
        ProbeInfo probeInfo = pm.getProbeInfo("repositoryStatus");
        assertTrue(result.isHealthy());
        assertTrue(probeInfo.getStatus().isSuccess());
        assertEquals("{\"repositoryStatus\":\"ok\"}", result.toJson());

        // delete the root document
        // the first check will still return healthy as the probe won't be run
        session.removeDocument(session.getRootDocument().getRef());
        session.save();

        result = pm.getOrRunHealthCheck("repositoryStatus");
        probeInfo = pm.getProbeInfo("repositoryStatus");
        assertEquals(1, probeInfo.getRunnedCount()); // check that the probe was run only once in the past 20 s
        assertTrue(result.isHealthy()); //
        assertTrue(probeInfo.getStatus().isSuccess());
        assertEquals("{\"repositoryStatus\":\"ok\"}", result.toJson());

        // force run again and should fail
        probeInfo = pm.runProbe(probeInfo);
        assertEquals(2, probeInfo.getRunnedCount());
        assertTrue(probeInfo.isInError());
        result = pm.getOrRunHealthCheck("repositoryStatus");
        assertTrue(probeInfo.getStatus().isFailure());
        assertEquals("{\"repositoryStatus\":\"failed\"}", result.toJson());
    }
}
