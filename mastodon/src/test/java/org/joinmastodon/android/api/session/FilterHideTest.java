package org.joinmastodon.android.api.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.joinmastodon.android.model.Filter;
import org.joinmastodon.android.model.FilterAction;
import org.joinmastodon.android.model.FilterResult;
import org.joinmastodon.android.model.Status;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class FilterHideTest{

	private static FilterResult makeResult(FilterAction action, Instant expiresAt){
		Filter filter=new Filter();
		filter.id="f1";
		filter.title="test";
		filter.filterAction=action;
		filter.expiresAt=expiresAt;
		FilterResult res=new FilterResult();
		res.filter=filter;
		return res;
	}

	@Test
	public void hidesStatusWithActiveHideFilter(){
		Status s=Status.ofFake("1", "hello", Instant.now());
		s.filtered=List.of(makeResult(FilterAction.HIDE, null));
		assertTrue(AccountSession.shouldHideStatus(s));
	}

	@Test
	public void doesNotHideWarnOrBlurOrExpiredHideFilter(){
		Status warn=Status.ofFake("1", "hello", Instant.now());
		warn.filtered=List.of(makeResult(FilterAction.WARN, null));
		assertFalse(AccountSession.shouldHideStatus(warn));

		Status blur=Status.ofFake("2", "hello", Instant.now());
		blur.filtered=List.of(makeResult(FilterAction.BLUR, null));
		assertFalse(AccountSession.shouldHideStatus(blur));

		Status expired=Status.ofFake("3", "hello", Instant.now());
		expired.filtered=List.of(makeResult(FilterAction.HIDE, Instant.now().minusSeconds(60)));
		assertFalse(AccountSession.shouldHideStatus(expired));
	}

	@Test
	public void hidesReblogWhenInnerStatusHasHideFilter(){
		Status outer=Status.ofFake("10", "", Instant.now());
		Status inner=Status.ofFake("11", "boosted", Instant.now());
		inner.filtered=List.of(makeResult(FilterAction.HIDE, Instant.now().plusSeconds(3600)));
		outer.reblog=inner;
		assertTrue(AccountSession.shouldHideStatus(outer));
	}

	@Test
	public void handlesNullStatus(){
		assertFalse(AccountSession.shouldHideStatus(null));
	}
}
