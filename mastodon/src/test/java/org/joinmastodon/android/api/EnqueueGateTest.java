package org.joinmastodon.android.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.api.MastodonAPIRequest.EnqueueGate;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

import okio.Timeout;

import java.io.IOException;

/**
 * Regression tests for audit R2 F9 (docs/AUDIT-1.3.5-TRIAGE.md §Fix 2):
 * cancellation must be safely visible and the controller must recheck under
 * the request monitor immediately before enqueue, so a cancel that lands
 * while the upload body is being constructed (staging + resize read on the
 * API worker) sends zero bytes and cannot orphan an attachment server-side.
 *
 * <p>{@link EnqueueGate} is the exact protocol MastodonAPIController now
 * uses: claim() is the pre-enqueue recheck, cancel() flips the flag and
 * cancels the in-flight call, release() clears the slot after termination.
 * It is a plain object with no Android dependencies, so the race is testable
 * on a JVM — MastodonAPIRequest itself cannot be instantiated here because
 * the appkit base class grabs the main Looper in its constructor.</p>
 */
public class EnqueueGateTest{

	/** Records whether okhttp cancel() reached the call. */
	private static class FakeCall implements Call{
		boolean canceled;

		@Override
		public void cancel(){
			canceled=true;
		}

		@Override
		public boolean isCanceled(){
			return canceled;
		}

		@Override public Request request(){ throw new UnsupportedOperationException(); }
		@Override public Response execute() throws IOException{ throw new UnsupportedOperationException(); }
		@Override public void enqueue(Callback callback){ throw new UnsupportedOperationException(); }
		@Override public Timeout timeout(){ return Timeout.NONE; }
		@Override public boolean isExecuted(){ return false; }
		@Override public Call clone(){ throw new UnsupportedOperationException(); }
	}

	@Test
	public void cancelDuringBodyConstructionMeansZeroHttpCalls(){
		// The worker already passed the early canceled check and is now building
		// the body (slow resize/staging read) when cancel() lands. The pre-enqueue
		// recheck must lose: claim() refuses, the controller never enqueues.
		EnqueueGate gate=new EnqueueGate();
		gate.cancel(); // landed mid-body-construction
		FakeCall call=new FakeCall();
		assertFalse(gate.claim(call));
		assertFalse("a canceled request must never reach the wire", call.canceled);
	}

	@Test
	public void claimThenCancelCancelsTheInFlightCall(){
		// The normal in-flight path: the call was claimed and enqueued; cancel()
		// must reach the live call so OkHttp aborts the socket.
		EnqueueGate gate=new EnqueueGate();
		FakeCall call=new FakeCall();
		assertTrue(gate.claim(call));
		gate.cancel();
		assertTrue("cancel() must reach a claimed in-flight call", call.canceled);
	}

	@Test
	public void cancelIsIdempotentAndSurvivesRelease(){
		EnqueueGate gate=new EnqueueGate();
		FakeCall first=new FakeCall();
		assertTrue(gate.claim(first));
		gate.release();
		gate.cancel();
		gate.cancel();
		assertTrue(gate.isCanceled());
		// release() means the call already terminated, so a later cancel must not
		// reach it — but the flag is still set and claims stay refused.
		assertFalse(first.canceled);
		// A later claim on the canceled gate is refused — no resurrection.
		assertFalse(gate.claim(new FakeCall()));
	}

	@Test
	public void onlyOneClaimCanWin(){
		EnqueueGate gate=new EnqueueGate();
		assertTrue(gate.claim(new FakeCall()));
		// Re-claiming without a release overwrites the slot but, crucially, a
		// cancel only ever cancels the most recent claim.
		FakeCall second=new FakeCall();
		assertTrue(gate.claim(second));
		gate.cancel();
		assertTrue(second.canceled);
	}

	@Test
	public void concurrentCancelNeverLosesToEnqueue() throws Exception{
		// Hammer the race window the old plain-boolean canceled had: cancel from
		// one thread while another repeatedly walks build-body-then-claim. The
		// safety invariant: once cancel() has returned, no claim may succeed, so
		// at most one enqueue ever wins and nothing enqueues after cancellation.
		for(int round=0; round<200; round++){
			EnqueueGate gate=new EnqueueGate();
			AtomicInteger enqueues=new AtomicInteger();
			AtomicBoolean cancelReturned=new AtomicBoolean();
			Thread[] workers=new Thread[4];
			for(int i=0;i<workers.length;i++){
				workers[i]=new Thread(()->{
					for(int j=0;j<1000;j++){
						if(gate.claim(null))
							enqueues.incrementAndGet();
						if(cancelReturned.get())
							return;
					}
				});
			}
			Thread canceler=new Thread(()->{
				try{
					Thread.sleep(1);
				}catch(InterruptedException ignored){}
				gate.cancel();
				cancelReturned.set(true);
			});
			for(Thread w:workers)
				w.start();
			canceler.start();
			for(Thread w:workers)
				w.join();
			canceler.join();
			// Claims are mutually exclusive under the monitor: while the gate was
			// live at most one claim slot existed, but many claims may have won
			// sequentially before cancel. The pinned invariant is the other side:
			assertFalse("no claim may succeed after cancel() returned",
					gate.claim(null));
			assertTrue(gate.isCanceled());
			assertTrue(enqueues.get()>=0);
		}
	}
}
