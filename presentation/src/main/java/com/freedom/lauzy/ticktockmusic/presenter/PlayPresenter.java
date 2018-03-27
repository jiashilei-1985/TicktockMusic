package com.freedom.lauzy.ticktockmusic.presenter;

import android.graphics.Bitmap;

import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.freedom.lauzy.interactor.FavoriteSongUseCase;
import com.freedom.lauzy.interactor.LrcUseCase;
import com.freedom.lauzy.ticktockmusic.base.BaseRxPresenter;
import com.freedom.lauzy.ticktockmusic.contract.PlayContract;
import com.freedom.lauzy.ticktockmusic.function.DefaultDisposableObserver;
import com.freedom.lauzy.ticktockmusic.function.RxHelper;
import com.freedom.lauzy.ticktockmusic.model.SongEntity;
import com.freedom.lauzy.ticktockmusic.model.mapper.FavoriteMapper;
import com.freedom.lauzy.ticktockmusic.utils.FileManager;
import com.freedom.lauzy.ticktockmusic.utils.ThemeHelper;
import com.lauzy.freedom.librarys.common.LogUtil;
import com.lauzy.freedom.librarys.imageload.ImageConfig;
import com.lauzy.freedom.librarys.imageload.ImageLoader;
import com.lauzy.freedom.librarys.view.blur.ImageBlur;
import com.lauzy.freedom.librarys.view.util.ColorUtil;
import com.lauzy.freedom.librarys.view.util.PaletteColor;
import com.lauzy.freedom.librarys.widght.music.lrc.Lrc;
import com.lauzy.freedom.librarys.widght.music.lrc.LrcParser;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;

/**
 * Desc : 播放Presenter
 * Author : Lauzy
 * Date : 2017/9/7
 * Blog : http://www.jianshu.com/u/e76853f863a9
 * Email : freedompaladin@gmail.com
 */
public class PlayPresenter extends BaseRxPresenter<PlayContract.View>
        implements PlayContract.Presenter {

    private FavoriteSongUseCase mFavoriteSongUseCase;
    private LrcUseCase mLrcUseCase;
    private FavoriteMapper mFavoriteMapper;
    private static final String TAG = "PlayPresenter";
    private HashMap<String, Integer> mColorMap = new HashMap<>();

    @Inject
    PlayPresenter(FavoriteSongUseCase favoriteSongUseCase, LrcUseCase lrcUseCase,
                  FavoriteMapper favoriteMapper) {
        mFavoriteSongUseCase = favoriteSongUseCase;
        mLrcUseCase = lrcUseCase;
        mFavoriteMapper = favoriteMapper;
    }

    @Override
    public void setCoverImgUrl(Object url) {
        if (getView() == null) {
            return;
        }
        String urlString = String.valueOf(url);
        ImageLoader.INSTANCE.display(getView().getContext(), new ImageConfig.Builder()
                .asBitmap(true)
                .url(url)
                .isRound(false)
                .intoTarget(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {

                        if (getView() == null) {
                            return;
                        }

                        getView().setPlayView(resource);
                        Bitmap bg = Bitmap.createBitmap(resource);
                        if (mColorMap.get(urlString) == null) {
                            PaletteColor.mainColorObservable(ThemeHelper.getThemeColorResId(getView().getContext()), resource)
                                    .subscribe(color -> {
                                        mColorMap.put(urlString, color);
                                        getView().setViewBgColor(color);
                                        judgeColorDepth(color);
                                    });
                        } else {
                            Integer color = mColorMap.get(urlString);
                            getView().setViewBgColor(color);
                            judgeColorDepth(color);
                        }
                        getView().setCoverBackground(ImageBlur.onStackBlur(bg, 50));
                    }

                    private void judgeColorDepth(Integer color) {
                        if (ColorUtil.isDarkColor(color)) {
                            getView().showLightViews();
                        } else {
                            getView().showDarkViews();
                        }
                    }
                }).build());
    }

    @Override
    public void addFavoriteSong(SongEntity entity) {
        mFavoriteSongUseCase.buildObservable(mFavoriteMapper.transform(entity))
                .compose(RxHelper.ioMain())
                .subscribe(value -> {
                    LogUtil.i(TAG, "add value is " + value);
                    if (value != -1 && getView() != null) {
                        getView().addFavoriteSong();
                    }
                });
    }

    @Override
    public void deleteFavoriteSong(long songId) {
        mFavoriteSongUseCase.deleteFavoriteSong(songId).subscribe(aLong -> {
            LogUtil.i(TAG, "delete value is " + aLong);
            if (getView() == null) {
                return;
            }
            getView().deleteFavoriteSong();
        });
    }

    @Override
    public void isFavoriteSong(long songId) {
        mFavoriteSongUseCase.isFavoriteSong(songId)
                .subscribe(aBoolean -> {
                    if (getView() == null) {
                        return;
                    }
                    getView().isFavoriteSong(aBoolean);
                });
    }

    @Override
    public void loadLrc(SongEntity entity) {
        LrcUseCase.Param param = new LrcUseCase.Param(entity.title, "");
        String fileName = entity.title + "-" + entity.artistName + ".lrc";
        File lrcFile = new File(FileManager.getInstance().getLrcDir().getAbsolutePath(), fileName);
        Observable.just(lrcFile)
                .flatMap(file -> {
                    if (file.exists() && file.canRead()) {
                        return Observable.just(file);
                    }
                    return mLrcUseCase.buildObservable(param)
                            .flatMap(body -> {
                                boolean saveFile = FileManager.getInstance().saveFile(body.byteStream(), fileName);
                                return saveFile ? Observable.just(file) : Observable.empty();
                            });
                })
                .map((Function<File, List<Lrc>>) file -> {
                    if (file == null || !file.exists()) {
                        return Collections.emptyList();
                    }
                    return LrcParser.parseLrcFromFile(file);
                })
                .compose(RxHelper.ioMain())
                .subscribeWith(new DefaultDisposableObserver<List<Lrc>>() {
                    @Override
                    protected void onStart() {
                        super.onStart();
                        if (getView() == null) {
                            return;
                        }
                        getView().startDownloadLrc();
                    }

                    @Override
                    public void onNext(List<Lrc> lrcs) {
                        super.onNext(lrcs);
                        if (getView() == null) {
                            return;
                        }
                        getView().downloadLrcSuccess(lrcs);
                    }

                    @Override
                    public void onError(Throwable e) {
                        super.onError(e);
                        if (getView() == null) {
                            return;
                        }
                        getView().downloadFailed(e);
                    }
                });
    }
}
