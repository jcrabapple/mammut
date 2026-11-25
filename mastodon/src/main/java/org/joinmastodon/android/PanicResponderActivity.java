package org.joinmastodon.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.joinmastodon.android.api.requests.oauth.RevokeOauthToken;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;

import java.util.List;
import java.util.Set;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;


public class PanicResponderActivity extends Activity {
    public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        // MAMMUT: audit S1.2 / Wave 2 S-M1 — resolve caller via OS-verified launcher/calling
        // package (never Binder.getCallingUid(), which returns our own UID on the main thread).
        // Wipe local credentials synchronously before exiting so offline panics cannot leave
        // tokens behind in accounts.db.
        if (intent != null && PANIC_TRIGGER_ACTION.equals(intent.getAction())
                && PanicTriggerPolicy.shouldRespond(resolveCallingPackage(), getPackageName())) {
            List<AccountSession> accounts = AccountSessionManager.getInstance().getLoggedInAccounts();
            for (AccountSession session : accounts) {
                revokeTokenBestEffort(session);
                AccountSessionManager.getInstance().removeAccount(session.getID());
            }
            ExitActivity.exit(this);
        }
        finishAndRemoveTask();
    }

    private String resolveCallingPackage(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            String launchedFrom=getLaunchedFromPackage();
            if(launchedFrom!=null)
                return launchedFrom;
        }
        return getCallingPackage();
    }

    private void revokeTokenBestEffort(AccountSession session){
        if(session==null || session.app==null || session.token==null)
            return;
        new RevokeOauthToken(session.app.clientId, session.app.clientSecret, session.token.accessToken)
                .setCallback(new Callback<>(){
                    @Override
                    public void onSuccess(Object result){}

                    @Override
                    public void onError(ErrorResponse error){}
                })
                .execNoAuth(session.domain);
    }

    // MAMMUT: audit S1.2 — pure allowlist decision, extracted for JVM tests. Ripple is the
    // canonical PanicKit trigger; the guardianproject panic package is kept for older
    // trigger builds. An unknown, self, or unresolvable caller never triggers a wipe.
    public static class PanicTriggerPolicy {
        private static final Set<String> TRUSTED_PACKAGES=Set.of(
                "info.guardianproject.ripple",
                "info.guardianproject.panic"
        );

        public static boolean shouldRespond(String callingPackage){
            return shouldRespond(callingPackage, null);
        }

        public static boolean shouldRespond(String callingPackage, String ownPackage){
            if(callingPackage==null || callingPackage.equals(ownPackage))
                return false;
            return TRUSTED_PACKAGES.contains(callingPackage);
        }
    }
}
