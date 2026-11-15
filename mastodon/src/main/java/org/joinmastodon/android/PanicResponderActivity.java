package org.joinmastodon.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;

import org.joinmastodon.android.api.requests.oauth.RevokeOauthToken;
import org.joinmastodon.android.api.session.AccountSession;
import org.joinmastodon.android.api.session.AccountSessionManager;

import java.util.Set;

import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;


public class PanicResponderActivity extends Activity {
    public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        // MAMMUT: audit S1.2 — the responder is exported, so ANY app could send the panic
        // action and force a logout of every account. Only respond when the caller resolves
        // to a trusted panic-app package (UID-based resolution can't be spoofed by the
        // sender); anything else is ignored.
        if (intent != null && PANIC_TRIGGER_ACTION.equals(intent.getAction())
                && PanicTriggerPolicy.shouldRespond(resolveCallingPackage())) {
            AccountSessionManager.getInstance().getLoggedInAccounts().forEach(accountSession -> logOut(accountSession.getID()));
            ExitActivity.exit(this);
        }
        finishAndRemoveTask();
    }

    private String resolveCallingPackage(){
        String caller=getCallingPackage();
        if(caller==null){
            try{
                caller=getPackageManager().getNameForUid(Binder.getCallingUid());
            }catch(SecurityException ignored){}
        }
        return caller;
    }

    private void logOut(String accountID){
        AccountSession session=AccountSessionManager.getInstance().getAccount(accountID);
        new RevokeOauthToken(session.app.clientId, session.app.clientSecret, session.token.accessToken)
                .setCallback(new Callback<>(){
                    @Override
                    public void onSuccess(Object result){
                        onLoggedOut(accountID);
                    }

                    @Override
                    public void onError(ErrorResponse error){
                        onLoggedOut(accountID);
                    }
                })
                .exec(accountID);
    }

    private void onLoggedOut(String accountID){
        AccountSessionManager.getInstance().removeAccount(accountID);
    }

    // MAMMUT: audit S1.2 — pure allowlist decision, extracted for JVM tests. Ripple is the
    // canonical PanicKit trigger; the guardianproject panic package is kept for older
    // trigger builds. An unknown or unresolvable caller never triggers a wipe.
    public static class PanicTriggerPolicy {
        private static final Set<String> TRUSTED_PACKAGES=Set.of(
                "info.guardianproject.ripple",
                "info.guardianproject.panic"
        );

        public static boolean shouldRespond(String callingPackage){
            return callingPackage!=null && TRUSTED_PACKAGES.contains(callingPackage);
        }
    }
}
