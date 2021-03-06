//Copyright zhkrb
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

//Create by zhkrb on 2019/9/7 22:12

package com.zhkrb.dragvideo.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.CutCornerTreatment;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.zhkrb.dragvideo.R;

public class CutCardView extends MaterialCardView {

    private final Context mcontext;
    private ShapeAppearanceModel.Builder mShapeAppearanceModel;
    private MaterialShapeDrawable mShapeDrawable;

    private boolean isAllCut;
    private boolean isTopLeftCut;
    private boolean isTopRightCut;
    private boolean isBottomLeftCut;
    private boolean isBottomRightCut;
    private ColorStateList backgoundColor;
    private float CutElevation;
    private float mAllCutRadius;
    private float mTopLeftRadius;
    private float mTopRightRadius;
    private float mBottomLeftRadius;
    private float mBottomRightRadius;
    private Drawable mForegoundDrawable;

    public CutCardView(Context context) {
        this(context,null);
    }

    public CutCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CutCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mcontext = context;
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.CutCardView);
        isAllCut = ta.getBoolean(R.styleable.CutCardView_AllCut, false);
        isTopLeftCut = ta.getBoolean(R.styleable.CutCardView_TopLeftCut, false);
        isTopRightCut = ta.getBoolean(R.styleable.CutCardView_TopRightCut, false);
        isBottomLeftCut = ta.getBoolean(R.styleable.CutCardView_BottomLeftCut, false);
        isBottomRightCut = ta.getBoolean(R.styleable.CutCardView_BottomRightCut, false);
        CutElevation = ta.getDimension(R.styleable.CutCardView_CutElevation,0);
        mAllCutRadius = ta.getDimension(R.styleable.CutCardView_AllCutRadius,0);
        mTopLeftRadius = ta.getDimension(R.styleable.CutCardView_TopLeftCutRadius,0);
        mTopRightRadius = ta.getDimension(R.styleable.CutCardView_TopRightCutRadius,0);
        mBottomLeftRadius = ta.getDimension(R.styleable.CutCardView_BottomLeftRadius,0);
        mBottomRightRadius = ta.getDimension(R.styleable.CutCardView_BottomRightRadius,0);
        mForegoundDrawable = ta.getDrawable(R.styleable.CutCardView_CutForeground);
//        mForegoundDrawable = getForeground();
        backgoundColor = getCardBackgroundColor();
        ta.recycle();
        init();
    }

    private void init() {
        if (isAllCut || isTopLeftCut || isTopRightCut || isBottomLeftCut || isBottomRightCut){
            mShapeAppearanceModel = new ShapeAppearanceModel().toBuilder();
//            mShapeAppearanceModel.setCornerRadius(0);
            if (isTopLeftCut){
                mShapeAppearanceModel.setTopLeftCorner(new CutCornerTreatment(mTopLeftRadius));
            }
            if (isTopRightCut){
                mShapeAppearanceModel.setTopRightCorner(new CutCornerTreatment(mTopRightRadius));
            }
            if (isBottomLeftCut){
                mShapeAppearanceModel.setBottomLeftCorner(new CutCornerTreatment(mBottomLeftRadius));
            }
            if (isBottomRightCut){
                mShapeAppearanceModel.setBottomRightCorner(new CutCornerTreatment(mBottomRightRadius));
            }
            if (isAllCut){
                mShapeAppearanceModel.setAllCorners(new CutCornerTreatment(mAllCutRadius));
            }
            setBackGround();
        }
    }

    private void setBackGround(){
        if (mShapeAppearanceModel == null){
            return;
        }
        mShapeDrawable = new MaterialShapeDrawable(mShapeAppearanceModel.build());
        mShapeDrawable.setPaintStyle(Paint.Style.FILL);
        mShapeDrawable.setFillColor(backgoundColor);
//        mShapeDrawable.setUseTintColorForShadow(true);
//        mShapeDrawable.setTint(getResources().getColor(R.color.textColor_black_medium));
        mShapeDrawable.setElevation(CutElevation);
        mShapeDrawable.setShadowCompatibilityMode(MaterialShapeDrawable.SHADOW_COMPAT_MODE_DEFAULT);

        setBackground(mShapeDrawable);
        if (mForegoundDrawable!=null){
            setForeground(mForegoundDrawable);
        }
    }


}
