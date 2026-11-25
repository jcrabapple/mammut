package org.joinmastodon.android.ui.displayitems;

import org.joinmastodon.android.model.Status;
import org.joinmastodon.android.model.Translation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Regression tests for translating poll posts and CW/filtered posts via Mammut Translate
 * (LibreTranslate), which populates translation.content without translation.poll or
 * translation.spoilerText.
 */
public class TranslationDisplayFallbackTest{

	@Test
	public void pollOptionReturnsNullWhenTranslationHasNoPoll(){
		Status s=new Status();
		s.translation=new Translation();
		s.translation.content="<p>Translated</p>";
		s.translation.poll=null;
		s.translationState=Status.TranslationState.SHOWN;

		assertNull(PollOptionStatusDisplayItem.translatedOptionTitle(s, 0));
	}

	@Test
	public void pollOptionReturnsNullWhenIndexOutOfBoundsOrHidden(){
		Status s=new Status();
		Translation t=new Translation();
		t.poll=new Translation.PollTranslation();
		Translation.PollOption opt=new Translation.PollOption();
		opt.title="Option A";
		t.poll.options=new Translation.PollOption[]{opt};
		s.translation=t;

		s.translationState=Status.TranslationState.SHOWN;
		assertEquals("Option A", PollOptionStatusDisplayItem.translatedOptionTitle(s, 0));
		assertNull(PollOptionStatusDisplayItem.translatedOptionTitle(s, 1));
		assertNull(PollOptionStatusDisplayItem.translatedOptionTitle(s, -1));

		s.translationState=Status.TranslationState.HIDDEN;
		assertNull(PollOptionStatusDisplayItem.translatedOptionTitle(s, 0));
	}

	@Test
	public void spoilerReturnsNullWhenLibreTranslateOmitsSpoilerText(){
		Status s=new Status();
		s.translation=new Translation();
		s.translation.content="<p>Translated</p>";
		s.translation.spoilerText=null;
		s.translationState=Status.TranslationState.SHOWN;

		assertNull(SpoilerStatusDisplayItem.translatedSpoilerTitle(s, Status.SpoilerType.CONTENT_WARNING));

		s.translation.spoilerText="";
		assertNull(SpoilerStatusDisplayItem.translatedSpoilerTitle(s, Status.SpoilerType.CONTENT_WARNING));
	}

	@Test
	public void spoilerTranslatesOnlyContentWarningWhenPresent(){
		Status s=new Status();
		s.translation=new Translation();
		s.translation.spoilerText="Translated CW";
		s.translationState=Status.TranslationState.SHOWN;

		assertEquals("Translated CW", SpoilerStatusDisplayItem.translatedSpoilerTitle(s, Status.SpoilerType.CONTENT_WARNING));
		assertNull(SpoilerStatusDisplayItem.translatedSpoilerTitle(s, Status.SpoilerType.FILTER));

		s.translationState=Status.TranslationState.HIDDEN;
		assertNull(SpoilerStatusDisplayItem.translatedSpoilerTitle(s, Status.SpoilerType.CONTENT_WARNING));
	}
}
