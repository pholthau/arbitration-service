/*
 * Copyright (C) 2017 pholthau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.citec.csra.allocation.cli;

import static de.citec.csra.allocation.cli.ExecutableResource.Completion.EXPIRE;
import static de.citec.csra.allocation.cli.ExecutableResource.Completion.MONITOR;
import static de.citec.csra.allocation.cli.ExecutableResource.Completion.RETAIN;
import de.citec.csra.allocation.srv.AllocationServer;
import java.util.concurrent.ExecutionException;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.Factory;
import rsb.RSBException;
import rsb.config.ParticipantConfig;
import rsb.config.TransportConfig;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;

/**
 *
 * @author pholthau
 */
public class ContinuityTest {

	private static final long TIMEOUT = RemoteAllocationService.TIMEOUT + 1000;

	@BeforeClass
	public static void initServer() throws InterruptedException {
		ParticipantConfig cfg = Factory.getInstance().getDefaultParticipantConfig();
		for (TransportConfig t : cfg.getTransports().values()) {
			t.setEnabled(t.getName().equalsIgnoreCase("INPROCESS"));
		}
		Factory.getInstance().setDefaultParticipantConfig(cfg);
		new Thread(() -> {
			try {
				AllocationServer a = AllocationServer.getInstance();
				a.activate();
				a.listen();
			} catch (InterruptedException | RSBException ex) {
				fail("Exception in server thread: " + ex);
			}
		}).start();
		Thread.sleep(200);
	}

	@AfterClass
	public static void shutdownServer() throws InterruptedException, RSBException {
		AllocationServer.getInstance().deactivate();
	}
	
	@Test
	public void testImmediateRelease() throws RSBException, InterruptedException, ExecutionException{
		long duration = 500;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, EXPIRE, "some-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
		};
		long before = System.currentTimeMillis();
		t.startup();
		t.getFuture().get();
		long after = System.currentTimeMillis();
		long runtime = after - before;
		
		assertTrue("block time smaller than estimated duration", runtime < duration);
	}
	
	@Test
	public void testContinousAllocation() throws RSBException, InterruptedException, ExecutionException{
		long duration = 500;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, MONITOR, "some-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
		};
		long before = System.currentTimeMillis();
		t.startup();
		t.getFuture().get();
		long after = System.currentTimeMillis();
		long runtime = after - before;
		
		assertTrue("at least block for allocated time", runtime >= duration);
	}
	
	@Test
	public void testLazyContinousAllocation() throws RSBException, InterruptedException, ExecutionException{
		long duration = 500;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, RETAIN, "some-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
		};
		long before = System.currentTimeMillis();
		t.startup();
		t.getFuture().get();
		long after = System.currentTimeMillis();
		
		long remaining = t.getRemote().getRemainingTime();
		long runtime = after - before;
		
		assertTrue("there is time remaining", remaining > 0);
		assertTrue("remaining time smaller than allocated", remaining < duration);
		assertTrue("remaining time plus runtime larger than allocated time", runtime + remaining >= duration);
	}
}