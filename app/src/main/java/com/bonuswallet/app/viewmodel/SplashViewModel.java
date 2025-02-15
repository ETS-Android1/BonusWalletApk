package com.bonuswallet.app.viewmodel;

import static com.bonuswallet.app.entity.tokenscript.TokenscriptFunction.ZERO_ADDRESS;
import static com.bonuswallet.ethereum.EthereumNetworkBase.BINANCE_TEST_ID;

import android.app.Activity;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bonuswallet.app.entity.CreateWalletCallbackInterface;
import com.bonuswallet.app.entity.Operation;
import com.bonuswallet.app.entity.Wallet;
import com.bonuswallet.app.entity.WalletType;
import com.bonuswallet.app.interact.FetchWalletsInteract;
import com.bonuswallet.app.repository.PreferenceRepositoryType;
import com.bonuswallet.app.service.KeyService;

import java.io.File;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class SplashViewModel extends ViewModel
{
    private static final String LEGACY_CERTIFICATE_DB = "CERTIFICATE_CACHE-db.realm";
    private static final String LEGACY_AUX_DB_PREFIX = "AuxData-";

    private final FetchWalletsInteract fetchWalletsInteract;
    private final PreferenceRepositoryType preferenceRepository;
    private final KeyService keyService;

    private final MutableLiveData<Wallet[]> wallets = new MutableLiveData<>();
    private final MutableLiveData<Wallet> createWallet = new MutableLiveData<>();

    SplashViewModel(FetchWalletsInteract fetchWalletsInteract,
                    PreferenceRepositoryType preferenceRepository,
                    KeyService keyService) {
        this.fetchWalletsInteract = fetchWalletsInteract;
        this.preferenceRepository = preferenceRepository;
        this.keyService = keyService;

        // increase launch count
        this.preferenceRepository.incrementLaunchCount();
    }

    public void fetchWallets()
    {
        fetchWalletsInteract
                .fetch()
                .subscribe(wallets::postValue, this::onError)
                .isDisposed();
    }

    //on wallet error ensure execution still continues and splash screen terminates
    private void onError(Throwable throwable) {
        wallets.postValue(new Wallet[0]);
    }

    public LiveData<Wallet[]> wallets() {
        return wallets;
    }
    public LiveData<Wallet> createWallet() {
        return createWallet;
    }

    public void createNewWallet(Activity ctx, CreateWalletCallbackInterface createCallback)
    {
        Completable.fromAction(() -> keyService.createNewHDKey(ctx, createCallback)) //create wallet on a computation thread to give UI a chance to complete all tasks
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
                .isDisposed();
    }

    private String stripFilename(String name)
    {
        int index = name.lastIndexOf(".apk");
        if (index > 0)
        {
            name = name.substring(0, index);
        }
        index = name.lastIndexOf("-");
        if (index > 0)
        {
            name = name.substring(index+1);
        }
        return name;
    }

    public void StoreHDKey(String address, KeyService.AuthenticationLevel authLevel)
    {
        if (!address.equals(ZERO_ADDRESS))
        {
            Wallet wallet = new Wallet(address);
            wallet.type = WalletType.HDKEY;
            wallet.authLevel = authLevel;
            fetchWalletsInteract.storeWallet(wallet)
                    .map(w -> { preferenceRepository.setCurrentWalletAddress(w.address); return w; })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(newWallet -> wallets.postValue(new Wallet[]{newWallet}), this::onError).isDisposed();
        }
        else
        {
            wallets.postValue(new Wallet[0]);
        }
    }

    public void completeAuthentication(Operation taskCode)
    {
        keyService.completeAuthentication(taskCode);
    }

    public void failedAuthentication(Operation taskCode)
    {
        keyService.failedAuthentication(taskCode);
    }

    public void cleanAuxData(Context ctx)
    {
        try
        {
            File[] files = ctx.getFilesDir().listFiles();
            for (File file : files)
            {
                String fileName = file.getName();
                if (fileName.startsWith(LEGACY_AUX_DB_PREFIX) || fileName.equals(LEGACY_CERTIFICATE_DB))
                {
                    deleteRecursive(file);
                }
            }
        }
        catch (Exception e)
        {
            //
        }
    }

    private void deleteRecursive(File fp)
    {
        if (fp.isDirectory())
        {
            File[] contents = fp.listFiles();
            if (contents != null)
            {
                for (File child : contents)
                    deleteRecursive(child);
            }
        }

        fp.delete();
    }

    public void setDefaultBrowser()
    {
        preferenceRepository.setActiveBrowserNetwork(BINANCE_TEST_ID);
    }

    public long getInstallTime() {
        return preferenceRepository.getInstallTime();
    }

    public void setInstallTime(long time) {
        preferenceRepository.setInstallTime(time);
    }
}
