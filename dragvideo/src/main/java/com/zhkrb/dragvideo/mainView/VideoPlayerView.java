/*
 * Copyright zhkrb
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
 * Create by zhkrb on 2019/9/7 22:12
 */

package com.zhkrb.dragvideo.mainView;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.customview.widget.ViewDragHelper;
import androidx.fragment.app.DialogFragment;

import com.zhkrb.dragvideo.NetworkUtil;
import com.zhkrb.dragvideo.R;
import com.zhkrb.dragvideo.ViewWrapper;
import com.zhkrb.dragvideo.bean.SettingBean;
import com.zhkrb.dragvideo.bean.UrlBean;
import com.zhkrb.dragvideo.bean.ValueSelectBean;
import com.zhkrb.dragvideo.contentView.IContent;
import com.zhkrb.dragvideo.utils.SettingListUtil;
import com.zhkrb.dragvideo.utils.ToastUtil;
import com.zhkrb.dragvideo.videoPlayer.IVideoPlayer;
import com.zhkrb.dragvideo.videoPlayer.ScaleVideoView;
import com.zhkrb.dragvideo.widget.PlayerSettingDialogFragment;
import com.zhkrb.dragvideo.widget.SettingSelectDialogFragment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class VideoPlayerView extends FrameLayout implements ScaleViewListener, PlayerSettingDialogFragment.onItemClickListener {

    private Context mContext;
    private int mAppearAnimId;

    private RelativeLayout mHeaderView;
    private NestedScrollView mDescView;
    private RelativeLayout mParentLayout;
    private ViewWrapper mHeaderWrapper;
    private ViewWrapper mDescWrapper;
    private ViewGroup mContentView;
    private IContent mInfoView;
    private ScaleVideoView mScaleVideoView;
    private VelocityTracker mTracker = null;

    //一些参数 单位 dp
    private int mSmillBottomHeight = 70;   //小控件时距离底部的高度
    private int mSmillLeftAndRight = 10;    //小控件时距离左右两侧宽度
    private int mSmillWidgetHeight = 55;    //小控件时控件高度  更改时注意修改ScaleVideoView
    private int mMaxHeight;
    private int mMaxWidth;

    //单位 px
    private int mTopMarginOrigin;   //小控件时距离顶部高度(需要计算)
    private int mDropDistance;      //完整变换滑动所需的距离 header高度/2到小控件模式高度/2的距离
    private int mDropHideDistance;  //完整滑动隐藏控件所需的距离 小控件高度/2到底部15dp的距离
    private int mFullCurrentHeaderHeight; //完整大小时 header的高度

    private int mVerticalFullHeight;    //视频为竖屏时最大高度
    private int mVerticalSmillHeight;   //视频为竖屏时最小高度
    private boolean isVerticalVideo = false;
    private boolean isScrollTop = true;

    private boolean isFirstUpdate = true;

    private ViewDragHelper mDragHelper;
    private int mMinVelocity;
    private int mMaxVelocity;
    private int mTouchSlop;
    private onHideFragmentListener mHideFragmentListener;

    private NetworkUtil mNetworkUtil;
    private List<UrlBean> mVideoUrlList;
    private int mSelectUrlPos = 0;
    private String mainUrl;


    public VideoPlayerView(@NonNull Context context) {
        this(context,null);
    }

    public VideoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public VideoPlayerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.VideoPlayView);
        mAppearAnimId = ta.getResourceId(R.styleable.VideoPlayView_appear_animation,R.anim.bottom_to_top);
        ta.recycle();
        mContext = context;
    }



    //加载
    public void show(){
        if (mContentView == null){
            throw new RuntimeException("must set content view");
        }
        if (getChildCount()!=0){
            removeAllViews();
        }
        LayoutInflater inflater = LayoutInflater.from(this.mContext);
        View view = inflater.inflate(R.layout.view_video_play, this, false);
        addView(view);
        mHeaderView = view.findViewById(R.id.view_header);
        mDescView = view.findViewById(R.id.view_desc);
        mDescView.setOnScrollChangeListener(mScrollChangeListener);
        mParentLayout = view.findViewById(R.id.parent);
        mHeaderWrapper = new ViewWrapper(mHeaderView);
        mDescWrapper = new ViewWrapper(mDescView);
        SeekBar seekBar = view.findViewById(R.id.progress);

        if (mInfoView != null){
            ((AppCompatActivity)mContext).getLifecycle().addObserver(mInfoView);
            mInfoView.addToParent(mDescView);
        }
        if (mScaleVideoView != null){
            mScaleVideoView.release();
            mScaleVideoView = null;
        }
        mScaleVideoView = new ScaleVideoView(mContext);
        mScaleVideoView.setViewListener(this);
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mScaleVideoView.setLayoutParams(lp);
        mScaleVideoView.initView(seekBar);
        mHeaderView.addView(mScaleVideoView);
        seekBar.bringToFront();

        mDragHelper = ViewDragHelper.create(this,1.0f,new dragViewCallback());
        final ViewConfiguration vc = ViewConfiguration.get(mContext);

        mTouchSlop = vc.getScaledTouchSlop();
        mMaxVelocity = vc.getScaledMaximumFlingVelocity();
        mMinVelocity = vc.getScaledMinimumFlingVelocity();
        if (getParent() == null){
            Animation animation = AnimationUtils.loadAnimation(mContext,mAppearAnimId);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (mHideFragmentListener != null){
                        mHideFragmentListener.onHide(true);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            setAnimation(animation);
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mContentView.addView(this);
            requestLayout();
            post(mMeasureRunable);
        }else {
            if (mScrollState == SCROLL_STATE_SMILL){
                scrollToNom(10000,0);
            }
        }
    }

    /**
     *
     * @param bundle
     * url
     * title
     * thumb
     * user
     */
    public void load(Bundle bundle) {
        if (mInfoView!=null){
            mInfoView.init(bundle);
        }
        mainUrl = bundle.getString("url","");
        String title = bundle.getString("title","");
        String thumb = bundle.getString("thumb","");
        String user = bundle.getString("user","");
        mScaleVideoView.initData(mainUrl,title,thumb,user);
        Map<String,String> header = new ArrayMap<>();
        header.put("Referer",bundle.getString("referer",""));
        header.put("User-Agent",bundle.getString("ua",""));
        mScaleVideoView.setHeader(header);
        mScaleVideoView.getVideoView().setLoading(true);
        if (mNetworkUtil != null){
            mNetworkUtil.setGetPlayUrlCallback(mCallback);
            mNetworkUtil.getUrlList(mainUrl);
        }else {
            throw new RuntimeException("must set NetworkUtil");
        }
    }

    public VideoPlayerView setContentView(ViewGroup contentView){
        mContentView = contentView;
        return this;
    }

    Runnable mMeasureRunable = new Runnable() {
        @Override
        public void run() {
            if (mMaxHeight==0&&mMaxWidth==0){
                mMaxWidth = getWidth();
                mMaxHeight = getHeight();
                mTopMarginOrigin = mMaxHeight - dp2px(mSmillWidgetHeight) - dp2px(mSmillBottomHeight);
                mDropDistance = mMaxHeight - dp2px(mSmillBottomHeight)
                                            - (mHeaderWrapper.getHeight()/2)
                                            - (dp2px(mSmillWidgetHeight)/2);
                mDropHideDistance = dp2px(mSmillWidgetHeight) + dp2px(mSmillBottomHeight)
                                        -dp2px(15);
                //当视频为竖屏时，进行滚动 最大高度=屏幕宽度/0.7
                //最小高度=屏幕宽度/0.8
                mVerticalFullHeight = (int) (mMaxWidth/0.7f);
                mVerticalSmillHeight = (int) (mMaxWidth/1.0f);
            }
        }
    };

    //    private int mDropDistance;      //完整变换滑动所需的距离 header高度/2到小控件模式高度/2的距离
    //    private int mFullCurrentHeaderHeight; //完整大小时 header的高度

    private float mDownMotionX;
    private float mDownMotionY;
    private float mDownRawY;
    private boolean isVerticalScroll = false;
    private boolean isHorizontalScroll = false;
    private boolean isDescVerticalScroll = false;

    private boolean canUsingEvent = false;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean intercept = false;
        final float x = ev.getX();
        final float y = ev.getY();
        final float rawy = ev.getRawY();
        final int action = ev.getAction();
        if ((isVerticalVideo && isViewHit(mDescView,(int) x,(int) y)) || isDescVerticalScroll){
            switch (action){
                case MotionEvent.ACTION_DOWN:
                    mDownMotionX = x;
                    mDownMotionY = y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = Math.abs(x - mDownMotionX);
                    float deltaY = Math.abs(y - mDownMotionY);
                    if (deltaY < mTouchSlop){
                        return false;
                    }
                    float moveY = y - mDownMotionY;
                    if ((moveY < 0 && isScrollTop && mHeaderWrapper.getHeight() != mVerticalSmillHeight) ||
                            moveY > 0 && isScrollTop){
                        intercept = true;
                        isDescVerticalScroll = true;
                    }
                    break;
            }
            return intercept;
        }
        if (!isViewHit(mHeaderView,(int) x,(int) y) && ! isVerticalScroll){
            return false;
        }
        switch (action){
            case MotionEvent.ACTION_DOWN:
                mDownMotionX = x;
                mDownMotionY = y;
                mDownRawY = rawy;
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaX = Math.abs(x - mDownMotionX);
                float deltaY = Math.abs(y - mDownMotionY);
                if ((deltaY > mTouchSlop && !isHorizontalScroll) || isVerticalScroll){
                    isVerticalScroll = true;
                    canUsingEvent = true;
                    intercept = true;
                }else if (deltaX > mTouchSlop){
                    isHorizontalScroll = true;
                    canUsingEvent = false;
                    intercept = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                isHorizontalScroll = false;
                break;
        }
        return intercept;
    }


    //0 nom 1 smill
    private int mScrollState = SCROLL_STATE_NOM;
    private static final int SCROLL_STATE_NOM = 0x000100;
    private static final int SCROLL_STATE_SMILL = 0x000101;
    // 0 nom 1 up 2 down
    private int mScrollOrient = ORIGIN_NOM;
    private static final int ORIGIN_NOM = 0x00020;
    private static final int ORIGIN_UP = 0x00021;
    private static final int ORIGIN_DOWN = 0x00022;


    private boolean isFirstMeasure = true;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((isVerticalVideo && isViewHit(mDescView,(int) event.getX(),(int) event.getY())) || isDescVerticalScroll){
            int action = event.getAction();
            float y = event.getY();
            switch (action){
                case MotionEvent.ACTION_DOWN:
                    isDescVerticalScroll = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float scrollY = y - mDownMotionY;
                    if ((scrollY > 0 && isScrollTop) || scrollY < 0){
                        scrollDescView((int) scrollY);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    isDescVerticalScroll = false;
                    break;
            }
            return false;
        }
        if (!canUsingEvent){
            return false;
        }
        int action = event.getAction();
        float y = event.getY();
        if (mScrollState == SCROLL_STATE_NOM&&isFirstMeasure){
            measureWidget();
            isFirstMeasure = false;
        }
        switch (action){
            case MotionEvent.ACTION_MOVE:
                if (mTracker == null){
                    mTracker = VelocityTracker.obtain();
                }else {
                    mTracker.clear();
                }
                mTracker.addMovement(event);
                float deltaY = y - mDownMotionY;
                switch (mScrollState){
                    case SCROLL_STATE_NOM:
                        int prog = (int) ((deltaY/(float) mDropDistance)*10000);
                        dropUpdate(prog);
                        break;
                    case SCROLL_STATE_SMILL:
                        if (mScrollOrient == ORIGIN_NOM){
                            if (deltaY == 0){
                                return true;
                            }
                            mScrollOrient = deltaY<0 ? ORIGIN_UP : ORIGIN_DOWN;
                        }
                        if (mScrollOrient == ORIGIN_UP){
                            int prog1 = (int) ((1f-(-deltaY/(float) mDropDistance))*10000);
                            dropUpdate(prog1);
                        }else {
                            deltaY = event.getRawY() - mDownRawY;
                            int prog2 = (int) ((deltaY/(float) mDropHideDistance)*100);
                            updateThird(prog2);
                        }
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                mTracker.computeCurrentVelocity(500);
                float yv = mTracker.getYVelocity();
                float deltaY1 = y - mDownMotionY;
                if (mScrollState == SCROLL_STATE_NOM){
                    int prog3 = (int) ((deltaY1/(float) mDropDistance)*10000);
                    if (prog3 > 1000 || yv > 40){
                        mScrollState = SCROLL_STATE_SMILL;
                        scrollToSmill(prog3,yv);
                    }else {
                        mScrollState = SCROLL_STATE_NOM;
                        scrollToNom(prog3,yv);
                    }
                }else {
                    if (mScrollOrient == ORIGIN_UP){
                        int prog4 = (int) ((1f-(-deltaY1/(float) mDropDistance))*10000);
                        if (prog4 < 9000 || yv < 0){
                            mScrollState = SCROLL_STATE_NOM;
                            scrollToNom(prog4,yv);
                        }else {
                            mScrollState = SCROLL_STATE_SMILL;
                            scrollToSmill(prog4,yv);
                        }
                    }else {
                        deltaY1 = event.getRawY() - mDownRawY;
                        int prog2 = (int) ((deltaY1/(float) mDropHideDistance)*100);
                        if (prog2 > 30 || yv > 40){
                            hideView(prog2,yv);
                        }else {
                            notHideView(prog2,yv);
                        }
                    }
                }
                isFirstMeasure = true;
                canUsingEvent = false;
                mScrollOrient = ORIGIN_NOM;
                isVerticalScroll = false;
                isHorizontalScroll = false;
                break;
        }
        return true;
    }


    private int computeSettleDuration(int dy, int yvel,int range) {
        float i = (float) reflectDragMethod("clampMag", yvel,  mMinVelocity, mMaxVelocity);
        yvel = (int) i;

        final int absDy = Math.abs(dy);
        final int absYVel = Math.abs(yvel);

        final float yweight = yvel != 0 ? (float) absYVel / absYVel :
                (float) absDy / absDy;

        int yduration = (int) reflectDragMethod("computeAxisDuration", dy, yvel, range);

        return (int) (yduration * yweight);
    }

    private Object reflectDragMethod(String name, Object... args) {
        for (Method method : mDragHelper.getClass().getDeclaredMethods()) {
            method.setAccessible(true);
            try {
                if (method.getName().equals(name)) {
                    if (args.length == 0) {
                        return method.invoke(mDragHelper);
                    } else {
                        return method.invoke(mDragHelper,args);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    private NestedScrollView.OnScrollChangeListener mScrollChangeListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
       isScrollTop = scrollY == 0;
    };


    private void notHideView(int i, float velocity) {
        if (i == 0){
            return;
        }
        mScrollOrient = ORIGIN_NOM;
        mScrollState = SCROLL_STATE_SMILL;
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setIntValues(i,0);
        valueAnimator.setDuration(computeSettleDuration(0 - i, (int) velocity, 100));
        valueAnimator.addUpdateListener(v -> updateThird((Integer) v.getAnimatedValue()));
        valueAnimator.setInterpolator(sInterpolator);
        valueAnimator.start();
    }

    private void hideView(int i, float velocity) {
        if (i == 100){
            release();
            return;
        }
        mScrollOrient = ORIGIN_NOM;
        mScrollState = SCROLL_STATE_SMILL;
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setIntValues(i,100);
        valueAnimator.setDuration(computeSettleDuration(100 - i, (int) velocity, 100));
        valueAnimator.addUpdateListener(v -> updateThird((Integer) v.getAnimatedValue()));
        valueAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                release();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        valueAnimator.setInterpolator(sInterpolator);
        valueAnimator.start();
    }

    private void scrollToNom(int i, float velocity) {
        if (i == 0){
            return;
        }
        mScrollOrient = ORIGIN_NOM;
        mScrollState = SCROLL_STATE_NOM;
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setIntValues(i,0);
        valueAnimator.setDuration(computeSettleDuration(0 - i, (int) velocity, 10000));
        valueAnimator.addUpdateListener(v -> dropUpdate((Integer) v.getAnimatedValue()));
        valueAnimator.setInterpolator(sInterpolator);
        valueAnimator.start();
    }

    private void scrollToSmill(int i, float velocity) {
        if (i == 10000){
            return;
        }
        mScrollOrient = ORIGIN_NOM;
        mScrollState = SCROLL_STATE_SMILL;
        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setIntValues(i,10000);
        valueAnimator.setDuration(computeSettleDuration(10000 - i, (int) velocity, 10000));
        valueAnimator.addUpdateListener(v -> dropUpdate((Integer) v.getAnimatedValue()));
        valueAnimator.setInterpolator(sInterpolator);
        valueAnimator.start();
    }

    /**
     * 从ViewDragHelper扒来的插值器
     * Interpolator defining the animation curve for mScroller
     */
    private static final Interpolator sInterpolator = t -> {
        t -= 1.0f;
        return t * t * t * t * t + 1.0f;
    };

    private void measureWidget() {
        mFullCurrentHeaderHeight = mHeaderWrapper.getHeight();
        mDropDistance = mMaxHeight - dp2px(mSmillBottomHeight)
                - (mHeaderWrapper.getHeight()/2)
                - (dp2px(mSmillWidgetHeight)/2);
    }

    private boolean isViewHit(View view, int x, int y) {
        if (view == null) {
            return false;
        }
        return x >= view.getLeft()
                && x < view.getRight()
                && y >= view.getTop()
                && y < view.getBottom();
    }

    //根据拖动距离的百分比进行变化 0-9000 9000-10000
    public void dropUpdate(int dis){
        if (mFullCurrentHeaderHeight == 0){
            measureWidget();
        }
        if (dis > 10000){
            dis = 10000;
        }else if (dis < 0){
            dis = 0;
        }
        if (dis > 0){
            mScaleVideoView.setPlayerState(IVideoPlayer.VIEW_STATE_SMILL);
            mParentLayout.setClipChildren(true);
            if (mHideFragmentListener != null){
                mHideFragmentListener.onHide(false);
            }
        }else {
            mScaleVideoView.setPlayerState(IVideoPlayer.VIEW_STATE_NOM);
            mParentLayout.setClipChildren(false);
            if (mHideFragmentListener != null){
                mHideFragmentListener.onHide(true);
            }
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, (Paint)null);
        if (dis <= 9000){
            updateFirst(dis);
        }else {
            updateSecond(dis);
        }
        setLayerType(View.LAYER_TYPE_NONE, (Paint)null);
    }

    private void updateFirst(int dis) {
        if (!isFirstUpdate){
            mScaleVideoView.setProgress(0);
            if (mDescView.getVisibility() != VISIBLE){
                mDescView.setVisibility(VISIBLE);
            }
            mHeaderView.setElevation(0);
            isFirstUpdate = true;
        }
        float prog = ((float)dis/9000f);
        int leftRightMargin = (int) (dp2px(mSmillLeftAndRight) * prog);
        mHeaderWrapper.setLeftMargin(leftRightMargin);
        mHeaderWrapper.setRightMargin(leftRightMargin);
        mDescWrapper.setLeftMargin(leftRightMargin);
        mDescWrapper.setRightMargin(leftRightMargin);
        int bottomMargin = (int) (dp2px(mSmillBottomHeight) * prog);
        mDescWrapper.setBottomMargin(bottomMargin);
        //一阶段变换为header小控件的高度+50dp
        int headerHeight = (int) (mFullCurrentHeaderHeight -
                                        (mFullCurrentHeaderHeight - dp2px(mSmillWidgetHeight+50))*prog);
        mHeaderWrapper.setHeight(headerHeight);
        int headerTopMargin = (int) ((mTopMarginOrigin-dp2px(50))*prog);
        mHeaderWrapper.setTopMargin(headerTopMargin);
        mParentLayout.getBackground().setAlpha(255 - (int) (255*prog));
        mHeaderView.requestLayout();
        mDescView.requestLayout();
        mParentLayout.invalidate();
    }

    private void updateSecond(int dis) {
        if (isFirstUpdate){
            setFirst();
            isFirstUpdate = false;
        }
        float prog = ((float)(dis-9000)/1000f);
        mScaleVideoView.setProgress(prog);
        int topMargin = (int) ((mTopMarginOrigin-dp2px(50)) + dp2px(50)*prog);
        mHeaderWrapper.setTopMargin(topMargin);
        int headerHeight = (int) (dp2px(mSmillWidgetHeight+50) - dp2px(50)*prog);
        mHeaderWrapper.setHeight(headerHeight);
        mHeaderView.requestLayout();
        mDescView.requestLayout();
    }

    private void setFirst() {
        int leftRightMargin = dp2px(mSmillLeftAndRight);
        mHeaderWrapper.setLeftMargin(leftRightMargin);
        mHeaderWrapper.setRightMargin(leftRightMargin);
        mDescWrapper.setLeftMargin(leftRightMargin);
        mDescWrapper.setRightMargin(leftRightMargin);
        mDescWrapper.setBottomMargin(dp2px(mSmillBottomHeight));
        mHeaderWrapper.setHeight(dp2px(mSmillWidgetHeight+50));
        mHeaderWrapper.setTopMargin(mTopMarginOrigin-dp2px(50));
        mParentLayout.getBackground().setAlpha(0);
        if (mDescView.getVisibility() != GONE){
            mDescView.setVisibility(GONE);
        }
        mHeaderView.setElevation(dp2px(5));
    }

    //三阶段变换，独立与1和2 进度: 0-100    完全隐藏高度40
    private void updateThird(int dis){
        if (dis < 0){
            dis = 0;
        }else if (dis > 100){
            dis = 100;
        }
        if (dis > 0){
            mScaleVideoView.setPlayerState(IVideoPlayer.VIEW_STATE_DOWN);
        }else {
            mScaleVideoView.setPlayerState(IVideoPlayer.VIEW_STATE_SMILL);
        }
        float prog = ((float)(dis/100f));
        setTranslationY(mDropHideDistance * prog);
        mHeaderView.setAlpha(1 - prog);
    }

    //当视频为竖屏时，进行滚动 最大高度=屏幕宽度/0.7
    //最小高度=屏幕宽度/0.8
    //大于 0向下  小于 0向上
    private void scrollDescView(int px){
        if (px > 0 && mHeaderWrapper.getHeight() >= mVerticalFullHeight){
            return;
        }
        if (px < 0 && mHeaderWrapper.getHeight() <= mVerticalSmillHeight){
            return;
        }
        int height = mHeaderWrapper.getHeight() + px;
        if (height < mVerticalSmillHeight){
            height = mVerticalSmillHeight;
        }
        if (height > mVerticalFullHeight){
            height = mVerticalFullHeight;
        }

        mHeaderWrapper.setHeight(height);
        mHeaderView.requestLayout();
        mDescView.requestLayout();
    }


    private int dp2px(int dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal,
                mContext.getResources().getDisplayMetrics());
    }


    public IContent getInfoView() {
        return mInfoView;
    }

    public void setInfoView(IContent infoView) {
        mInfoView = infoView;
    }

    @Override
    public void onVideoFirstPrepared(int width, int height) {
        int currentHeight = (int) (mMaxWidth / ((float)width/(float)height));
        if (currentHeight > mVerticalFullHeight){
            isVerticalVideo = true;
            currentHeight = mVerticalFullHeight;
        }else {
            isVerticalVideo = false;
        }

        if (mScrollState != SCROLL_STATE_NOM){
            mFullCurrentHeaderHeight = currentHeight;
        }else {
            mHeaderWrapper.setHeight(currentHeight);
            mHeaderView.requestLayout();
        }
    }

    public void release(){
        setTranslationY(0);
        setAlpha(1f);
        mHeaderWrapper.setHeight(dp2px(195));
        mScrollState = SCROLL_STATE_NOM;
        mScrollOrient = ORIGIN_NOM;
        ((ViewGroup)getParent()).removeView(this);
        mScaleVideoView.release();
        mScaleVideoView = null;
//        showLoadProgress(false);
//        showFailedIcon(false);
        if (mTracker != null){
            mTracker.recycle();
        }
    }

    @Override
    public void onBtnViewDown() {
        mScrollState = SCROLL_STATE_SMILL;
        mScaleVideoView.setPlayerState(IVideoPlayer.VIEW_STATE_SMILL);
        scrollToSmill(0,0);
    }

    @Override
    public void onBtnMore() {
        PlayerSettingDialogFragment fragment = new PlayerSettingDialogFragment();
        fragment.setAdapter(SettingListUtil.
                        getSettingList(mContext,
                                mVideoUrlList,
                                mSelectUrlPos,
                                mScaleVideoView.getVideoView().getPlaySpeed()),
                this);
        fragment.show(((AppCompatActivity)mContext).getSupportFragmentManager(),"PlayerSettingDialogFragment");
    }

    @Override
    public void onClickViewToNom() {
        scrollToNom(10000,0);
    }

    @Override
    public void onClickViewClose() {
        hideView(0,0);
    }

    @Override
    public void onReload() {
        mNetworkUtil.getUrlList(mainUrl);
        mScaleVideoView.getVideoView().setLoading(true);
    }

    public void setNetworkUtil(NetworkUtil networkUtil) {
        mNetworkUtil = networkUtil;
    }

    private NetworkUtil.GetPlayUrlCallback mCallback = new NetworkUtil.GetPlayUrlCallback() {
        @Override
        public void onSuccess(List<UrlBean> list) {
            mVideoUrlList = list;
            mScaleVideoView.getVideoView().setLoading(false);
            mScaleVideoView.playUrl(mVideoUrlList.get(mSelectUrlPos).getUri(),
                    mScaleVideoView.getVideoView().getPlayingPos());
        }

        @Override
        public void onFailed(String result) {
            ToastUtil.show(mContext,R.string.url_get_failed);
            mScaleVideoView.getVideoView().setFailed((String) mContext.getText(R.string.url_get_failed_2));
        }
    };




    @Override
    public void onItemClick(DialogFragment fragment, SettingBean bean, int pos) {
        switch (bean.getId()){
            case 0: //画质
                getUrlList();
                break;
            case 1: //播放速率
                openSelectDialog(SettingListUtil.getSpeedList(mContext),
                        String.valueOf(mScaleVideoView.getVideoView().getPlaySpeed()),
                        mSpeedSelectListener);
                break;
        }
        fragment.dismiss();
    }

    private void getUrlList() {
//        NetworkUtil networkUtil = mNetworkUtil.clone();
        mNetworkUtil.setGetPlayUrlCallback(new NetworkUtil.GetPlayUrlCallback() {
                    @Override
                    public void onSuccess(List<UrlBean> list) {
                        mVideoUrlList = list;
                        openSelectDialog(SettingListUtil.getUrlList(mVideoUrlList),
                                String.valueOf(mSelectUrlPos),
                                mUrlSelectListener);
                    }

                    @Override
                    public void onFailed(String result) {
                        ToastUtil.show(mContext,R.string.url_get_failed);
                    }
                }
        );
        mNetworkUtil.getUrlList(mainUrl);
    }

    private void openSelectDialog(List<ValueSelectBean> list,String value,SettingSelectDialogFragment.onItemClickListener listener){
        SettingSelectDialogFragment fragment = new SettingSelectDialogFragment();
        fragment.setListener(listener);
        fragment.setAdapter(list,value);
        fragment.show(((AppCompatActivity)mContext).getSupportFragmentManager(),"SettingSelectDialogFragment");
    }

    private SettingSelectDialogFragment.onItemClickListener mUrlSelectListener =
            (fragment, bean, pos) -> {
        int pos1 = Integer.valueOf(bean.getValue());
        if (mSelectUrlPos == pos1){
            return;
        }
        mSelectUrlPos = pos1;
        mScaleVideoView.playUrl(mVideoUrlList.get(mSelectUrlPos).getUri(),
                mScaleVideoView.getVideoView().getPlayingPos());
        fragment.dismiss();
    };

    private SettingSelectDialogFragment.onItemClickListener mSpeedSelectListener =
            (fragment, bean, pos) -> {
                mScaleVideoView.getVideoView().setPlaySpeed(Float.valueOf(bean.getValue()));
                fragment.dismiss();
            };

    public boolean isFullScreen() {
        if (mScaleVideoView!=null){
            return mScaleVideoView.getVideoView().isFullScreen();
        }
        return false;
    }

    public boolean isNom() {
        if (getParent()!=null){
            return mScrollState == SCROLL_STATE_NOM;
        }
        return false;
    }

    public void exitFullScreen() {
        if (mScaleVideoView != null){
            mScaleVideoView.getVideoView().exitFullScreen();
        }
    }

    public void toSmill() {
        scrollToSmill(0,0);
    }

    public void setHideFragmentListener(onHideFragmentListener hideFragmentListener) {
        mHideFragmentListener = hideFragmentListener;
    }


    private class dragViewCallback extends ViewDragHelper.Callback {
        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
            return false;
        }
    }

    public interface onHideFragmentListener{
        void onHide(boolean hide);
    }


}
