package org.onepf.life.google;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import org.onepf.life.BasePurchaseHelper;
import org.onepf.life.GameActivity;
import org.onepf.life.Market;
import org.onepf.life.google.util.IabHelper;
import org.onepf.life.google.util.IabResult;
import org.onepf.life.google.util.Inventory;
import org.onepf.life.google.util.Purchase;

public class GooglePlayHelper extends BasePurchaseHelper {
    IabHelper mHelper;
    Context context;
    GameActivity parent;

    private boolean isReady = false;

    private final String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhh9ee2Ka+dO2UCkGSndfH6/5jZ/kgILRguYcp8TpkAus6SEU8r8RSjYf4umAVD0beC3e7KOpxHxjnnE0z8A+MegZ11DE7/jQw4XQ0BaGzDTezCJrNUR8PqKf/QemRIT7UaNC0DrYE07v9WFjHFSXOqChZaJpih5lC/1yxwh+54IS4wapKcKnOFjPqbxw8dMTA7b0Ti0KzpBcexIBeDV5FT6FimawfbUr/ejae2qlu1fZdlwmj+yJEFk8h9zLiH7lhzB6PIX72lLAYk+thS6K8i26XbtR+t9/wahlwv05W6qtLEvWBJ5yeNXUghAw+Hk/x8mwIlrsjWMQtt1W+pBxYQIDAQAB";

    private final static String SKU_ORANGE_CELLS = "orange_cells_subscription";
    private final static String SKU_FIGURES = "figures";
    private final static String SKU_CHANGES = "changes";
    private final static int RC_REQUEST = 10001; // i don't know what does it
    // mean...

    public GooglePlayHelper(GameActivity context) {
        parent = context;
        this.context = context;

        mHelper = new IabHelper(context, publicKey);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    Log.d(GameActivity.TAG,
                            "Problem setting up In-app Billing: " + result);
                    return;
                }
                isReady = true;
                mHelper.queryInventoryAsync(mGotInventoryListener);
            }
        });
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return mHelper.handleActivityResult(requestCode, resultCode, data);
    }


    public boolean isReady() {
        return isReady;
    }

    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result,
                                             Inventory inventory) {
            if (result.isFailure()) {
                parent.alert("Failed to load account information");
                return;
            }

            final SharedPreferences.Editor editor = getSharedPreferencesEditor();

            Purchase figuresPurchase = inventory
                    .getPurchase(GameActivity.FIGURES);
            boolean figures = (figuresPurchase != null && verifyDeveloperPayload(figuresPurchase));
            editor.putBoolean(GameActivity.FIGURES, figures);

            Purchase orangeCellsPurchase = inventory
                    .getPurchase(GameActivity.ORANGE_CELLS);
            boolean orangeCells = (orangeCellsPurchase != null && verifyDeveloperPayload(orangeCellsPurchase));
            editor.putBoolean(GameActivity.ORANGE_CELLS, orangeCells);
            editor.commit();

            parent.update();
        }
    };

    private boolean verifyDeveloperPayload(Purchase p) {
        return true;
    }

    @Override
    public void onBuyOrangeCells() {
        final SharedPreferences settings = getSharedPreferencesForCurrentUser();
        boolean orangeCells = settings.getBoolean(GameActivity.ORANGE_CELLS,
                false);
        if (orangeCells) {
            parent.alert("Orange cells has already bought!");
        } else {
            try {
                mHelper.launchSubscriptionPurchaseFlow((Activity) context,
                        SKU_ORANGE_CELLS, RC_REQUEST,
                        mPurchaseFinishedListener, "");
            } catch (IllegalStateException e) {
                Log.d(GameActivity.TAG, "too many async operations");
            }
        }
    }

    @Override
    public void onBuyFigures() {
        final SharedPreferences settings = getSharedPreferencesForCurrentUser();
        boolean figures = settings.getBoolean(GameActivity.FIGURES, false);
        if (figures) {
            parent.alert("Figures has already bought!");
        } else {
            try {
                mHelper.launchPurchaseFlow((Activity) context, SKU_FIGURES,
                        RC_REQUEST, mPurchaseFinishedListener, "");
            } catch (IllegalStateException e) {
                Log.d(GameActivity.TAG, "too many async operations");
            }
        }
    }

    @Override
    public void onBuyChanges() {
        try {
            mHelper.launchPurchaseFlow((Activity) context, SKU_CHANGES,
                    RC_REQUEST, mPurchaseFinishedListener, "");
        } catch (IllegalStateException e) {
            Log.d(GameActivity.TAG, "too many async operations");
        }
    }

    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(GameActivity.TAG, "Purchase finished: " + result
                    + ", purchase: " + purchase);
            if (result.isFailure()) {
                parent.alert("Error purchasing: " + result);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                parent.alert("Error purchasing. Authenticity verification failed.");
                return;
            }

            Log.d(GameActivity.TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_ORANGE_CELLS)) {
                final SharedPreferences.Editor editor = getSharedPreferencesEditor();
                editor.putBoolean(GameActivity.ORANGE_CELLS, true);
                editor.commit();
            }

            if (purchase.getSku().equals(SKU_FIGURES)) {
                final SharedPreferences.Editor editor = getSharedPreferencesEditor();
                editor.putBoolean(GameActivity.FIGURES, true);
                editor.commit();
            }

            if (purchase.getSku().equals(SKU_CHANGES)) {
                mHelper.consumeAsync(purchase, new IabHelper.OnConsumeFinishedListener() {

                    @Override
                    public void onConsumeFinished(Purchase purchase,
                                                  IabResult result) {
                        if (result.isFailure()) {
                            Log.d(GameActivity.TAG, "Fail consuming item");
                            return;
                        }
                        final SharedPreferences settings = getSharedPreferencesForCurrentUser();
                        int changes = settings.getInt(GameActivity.CHANGES, 0) + 50;
                        final SharedPreferences.Editor editor = getSharedPreferencesEditor();
                        editor.putInt(GameActivity.CHANGES, changes);
                        editor.commit();
                        parent.update();
                    }
                });

            }

            // check other options ...

            parent.update();
        }
    };

    public static void init(GameActivity parent) {
        PackageManager myapp = parent.getPackageManager();
        String installer = myapp.getInstallerPackageName("org.onepf.life");
        if (installer.equals("com.android.vending")) {
            parent.setPurchaseHelper(new GooglePlayHelper(parent));
        }
    }

    @Override
    public Market getMarket() {
        return Market.GOOGLE_PLAY;
    }

    private SharedPreferences.Editor getSharedPreferencesEditor() {
        return parent.getSharedPreferencesForCurrentUser().edit();
    }

    private SharedPreferences getSharedPreferencesForCurrentUser() {
        return parent.getSharedPreferences(parent.getCurrentUser(),
                Context.MODE_PRIVATE);
    }
}
