package com.lq.horizontalwheelview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

public class HorizontalWheelView extends View{

    private static final String TAG = "wheel";

    private static final String DU = "°";

    private static final int VISIBLE_DEGREE_D = 90;
    private static final double VISIBLE_DEGREE = VISIBLE_DEGREE_D / 180f * Math.PI;
    private static final double VISIBLE_HALF_DEGREE = VISIBLE_DEGREE/2;

    private static final double REL_R = 0.5f / Math.sin(VISIBLE_DEGREE / 2);
    private static final double PI_DOU_2 = 2 * Math.PI;
    private static final float SIN60 = (float)Math.sin(Math.PI / 3);

    public static final int sCrimsonColor = 0xFFD33A2A;//0xFFDD5847;

    private static Theme sTheme = Theme.BLACK;

    private Paint mLeftMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint mRightMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint mMaskAllPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint mLayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 绘制圆盘刻度的paint */
    private Paint mCalibrationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 绘制圆盘中心旋转度数的paint */
    private Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 绘制圆盘上下三角形游标的paint */
    private Paint mCursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 绘制偏移Rect的paint */
    private Paint mOffsetXPaint = new Paint();

    {
        mCalibrationPaint.setStyle(Paint.Style.STROKE);
        mCalibrationPaint.setColor(sTheme.getCaliColor());
        mCalibrationPaint.setStrokeWidth(3);

        mTextPaint.setTextSize(36);//default
        mTextPaint.setColor(sTheme.getTextColor());

        mCursorPaint.setStyle(Paint.Style.FILL);
        mCursorPaint.setColor(sTheme.getCursorColor());

        mOffsetXPaint.setStyle(Paint.Style.FILL);
        mOffsetXPaint.setColor(sCrimsonColor);
        mOffsetXPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    }

    /** 圆盘的半径 */
    private double mRadius;

    private float mCalibrationRadius;

    /** 圆盘中间刻度的长度 */
    private int mCaliLength;

    /** 圆盘转过的角度 */
    private float mCurrentDegree;

    /** 刻度集合 */
    private ArrayList<Calibration> mCalibrations = new ArrayList<Calibration>();

    /** 向左旋转的最大角度 */
    private int mMinDegree;

    /** 向右旋转的最大角度 */
    private int mMaxDegree;

    /** 游标与刻度的上下间距 */
    private int mCursorPad;

    /** textDrawPoint[0]:文本宽度，textDrawPoint[1]:文本高度，textDrawPoint[2]:文本top padding */
    private float[] mTextDrawBounds = new float[3];

    private Rect mCenterNumRect = new Rect();

    private PointF mTextPoint = new PointF();

    public HorizontalWheelView(Context context) {
        super(context);
        initDefaultValue();
        setBackgroundColor(sTheme.getBgColor());
    }

    public HorizontalWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initDefaultValue();
        setBackgroundColor(sTheme.getBgColor());
    }

    private void initDefaultValue() {
        mCursorPad = 20;
        float textSize = getPixelsFromSP(14);
        mTextPaint.setTextSize(textSize);
        mTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setRegion(45);
        getTextBounds(getProgressStr(-45), mTextPaint, mTextDrawBounds);
        mCaliLength = (int)(mTextDrawBounds[1] * 4 / 7);
        mCalibrationRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width ,height;
        setMeasuredDimension(width = MeasureSpec.getSize(widthMeasureSpec), height = measureHeight(heightMeasureSpec));
        Log.i(TAG, "onMeasure()--width=" + width + ",height=" + height);
    }

    private int measureHeight(int heightMeasureSpec) {
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int needHeight = (int)(getPaddingTop() + getPaddingBottom() + mCaliLength + mCursorPad * 2 + 4 * 2 + mCaliLength * SIN60 * 2);
        if (heightSpecMode == MeasureSpec.EXACTLY) {
            return Math.max(height, needHeight);
        } else {
            return Math.min(height, needHeight);
        }
    }

    /** 设置刻度旋转范围 */
    public void setRegion(int degree) {
        int d = Math.abs(degree);
        setRegion(-d, d);
    }

    /** 设置刻度旋转范围 */
    private void setRegion(int min, int max) {
        mMinDegree = min;
        mMaxDegree = max;
    }

    /** 剔除掉所有padding之后的可绘制区域 */
    private Rect mValidDrawBounds = new Rect();
    /** 以view的中点为原点的总的偏移量 */
    private float mTotalOffsetX = 0;
    private float mPreDownX = 0;
    private float mOffsetX = 0;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            mPreDownX = event.getX();
            if (mOnSeekBarChangeListener != null) {
                mOnSeekBarChangeListener.onStartTrackingTouch(this);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            mOffsetX = (event.getX() - mPreDownX);
            mTotalOffsetX += mOffsetX;
            mPreDownX = event.getX();
            calProgress();
            if (mCurrentDegree >= mMaxDegree || mCurrentDegree <= mMinDegree) {
                calIntProgress();
                if (mOnSeekBarChangeListener != null) {
                    mOnSeekBarChangeListener.onProgressChanged(this, mCurrentDegree, false);
                }
                invalidate();
                return true;
            }
            updateCalibrations(calDegreeByOffset(mOffsetX));
            if (mOnSeekBarChangeListener != null) {
                mOnSeekBarChangeListener.onProgressChanged(this, mCurrentDegree, false);
            }
            invalidate();
        } else if (action == MotionEvent.ACTION_UP) {
            calIntProgress();
            if (mOnSeekBarChangeListener != null) {
                mOnSeekBarChangeListener.onStopTrackingTouch(this);
            }
            Log.i(TAG, "ACTION_UP mCurrentDegree=" + mCurrentDegree);
        }
        return true;
    }

    private float getPixelsFromSP(float value) {
        Resources r = getResources();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                r.getDisplayMetrics());
    }

    /** 根据总的偏移量计算旋转角度 */
    private void calProgress() {
        int viewW = mValidDrawBounds.width();
        // 小于０为顺时针旋转，角度为正，大于０则相反
        mTotalOffsetX = clamp(mTotalOffsetX, -viewW / 2f, viewW / 2f);
        float ratio = mTotalOffsetX * 2 / viewW;
        mCurrentDegree = clamp(ratio * mMaxDegree, mMinDegree, mMaxDegree);
        mCurrentDegree = -mCurrentDegree;
        //Log.i(TAG, "calProgress ratio="+ratio+" mTotalOffset="+mTotalOffset);

        updateTopCursor(mTotalOffsetX);

        updateTextBasePoint(getProgressStr(mCurrentDegree));
        //getTextBounds(String.valueOf(mCurrentDegree + "°"), mTextPaint, textDrawBounds);
        //calTextDrawBounds(mValidDrawBounds);
    }

    /** ACTION_UP 时调用，将旋转角度强转为整数 */
    private void calIntProgress() {
        float flag = mCurrentDegree >= 0 ? 0.5f : -0.5f;
        mCurrentDegree = (int)clamp(mCurrentDegree + flag, mMinDegree, mMaxDegree);
        calOffsetByDegree(mCurrentDegree);
        Log.i(TAG, "calIntProgress mTotalOffset="+mTotalOffsetX);
        updateTopCursor(mTotalOffsetX);
        updateTextBasePoint(getProgressStr(mCurrentDegree));
    }

    private static float clamp(float x, float min, float max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    private double calDegreeByOffset(double offset) {
        return Math.asin(offset / mRadius);
    }

    private void updateCalibrations(double dDegree) {
        if (mCalibrations.size() == 0 || dDegree == 0) {
            return;
        }
        int halfW = mValidDrawBounds.centerX();
        for (Calibration c : mCalibrations) {
            c.degree += dDegree;
            if (isFrontSide(c)) {
                c.isFront = true;
                c.calX(mRadius, halfW);
                c.calL(mCaliLength);
            } else {
                c.isFront = false;
            }
        }
    }

    private void updateTopCursor(float x) {
        if(cursorTop != null){
            cursorTop.transXTo(x);
        }
    }

    /** 上下三角形游标 */
    private TriCursor cursorTop, cursorBottom;
    @Override
    protected void onDraw(Canvas canvas) {
        int viewW = canvas.getWidth();
        int viewH = canvas.getHeight();
        initView(viewW, viewH);

        calCalibrationsPath();
        // calCalibrationsPoints();

        canvas.saveLayer(0, 0, viewW, viewH, mLayerPaint, Canvas.ALL_SAVE_FLAG);
        // draw calibrations path
        canvas.drawPath(mCalibrationsPath, mCalibrationPaint);

        // draw calibrations point
        /*
        for (PointF p : mCalibrationsPoint) {
            canvas.drawCircle(p.x, p.y, mCalibrationRadius, mCalibrationPaint);
        }
        */

        // draw progress mask
        if (mTotalOffsetX >= 0) {
            canvas.drawRect(viewW/2, 0, viewW/2 + mTotalOffsetX, viewH, mOffsetXPaint);
        } else {
            canvas.drawRect(viewW/2 + mTotalOffsetX, 0, viewW/2, viewH, mOffsetXPaint);
        }
        canvas.restore();

        // draw mask
        canvas.drawRect(leftMaskRect, mLeftMaskPaint);
        canvas.drawRect(rightMaskRect, mRightMaskPaint);
        canvas.drawRect(0, 0, viewW, viewH, mMaskAllPaint);

        // draw straighten text
        canvas.save();
        canvas.clipRect(mCenterNumRect);
        canvas.drawColor(sTheme.getTextBgColor());
        canvas.drawText(getProgressStr(mCurrentDegree), mTextPoint.x, mTextPoint.y, mTextPaint);
        canvas.restore();

        // draw cursor
        mCursorPaint.setColor(sCrimsonColor);
        canvas.drawPath(cursorTop.path, mCursorPaint);
        mCursorPaint.setColor(sTheme.getCursorColor());
        canvas.drawPath(cursorBottom.path, mCursorPaint);
    }

    private Rect leftMaskRect = new Rect();
    private Rect rightMaskRect = new Rect();

    private void initView(int viewW, int viewH) {
        if (mCalibrations.size() > 0) {
            return;
        }
        mValidDrawBounds.set(getPaddingLeft(), getPaddingTop(), viewW - getPaddingRight(), viewH - getPaddingBottom());
        mRadius = mValidDrawBounds.width() * REL_R;
        //getTextBounds("5°", mTextPaint, textDrawBounds);
        //length = (int)textDrawBounds[1];
        mCursorPad = 20;
        calOffsetByDegree(mCurrentDegree);

        initCursor(mValidDrawBounds);
        initCalibrationPath(mValidDrawBounds);
        initTextDrawBounds(mValidDrawBounds);
        initMaskPaint(mValidDrawBounds);
    }

    private void initCursor(Rect validRect) {
        // 初始化游标
        int cursorEdge = mCaliLength * 4 / 5;
        int cursorH = (int) (cursorEdge * SIN60);
        int y = (validRect.height() - cursorEdge - cursorH * 2 - mCursorPad * 2) / 2;
        cursorTop = new TriCursor();
        cursorTop.face = false;
        cursorTop.l = cursorEdge;
        cursorTop.p = new PointF(validRect.centerX(), validRect.top + y);
        cursorTop.initPath();
        cursorTop.transXTo(mTotalOffsetX);

        cursorBottom = new TriCursor();
        cursorBottom.face = true;
        cursorBottom.l = cursorEdge;
        cursorBottom.p = new PointF(validRect.centerX(), validRect.bottom - y);
        cursorBottom.initPath();
    }

    private void initCalibrationPath(Rect validRect) {
        mCalibrations.clear();
        int halfW = validRect.centerX();
        Log.i(TAG, "initCalibrationPath REL_R=" + REL_R + " mValidDrawBounds=" + mValidDrawBounds);
        for (int i = -45; i < 45; i++) {
            Calibration c = new Calibration();
            c.calDegree(i * 4);
            c.isFront = isFrontSide(c);
            c.calX(mRadius, halfW);
            c.calL(mCaliLength);
            mCalibrations.add(c);
        }
    }

    private void resetCalibrations() {
        if (!mValidDrawBounds.isEmpty()) {
            initCalibrationPath(mValidDrawBounds);
        }
    }

    private void initTextDrawBounds(Rect validRect) {
        int textH = (int)mTextDrawBounds[1], textW = (int)mTextDrawBounds[0];
        int left = validRect.centerX() - textW / 2;
        int top = validRect.centerY() - textH / 2;
        int padT_B = 4;
        int padL_R = 12;
        mCenterNumRect.set(left - padL_R, top - padT_B, left + textW + padL_R, top + textH + padT_B);

        String text = getProgressStr(mCurrentDegree);
        float realTextWidth = mTextPaint.measureText(text, 0, text.length());
        float textX = mValidDrawBounds.centerX() - realTextWidth/2;
        float textY = mValidDrawBounds.top + (mValidDrawBounds.height() - textH)/2 - mTextDrawBounds[2];
        mTextPoint.set(textX, textY);
    }

    private void initMaskPaint(Rect validRect) {
        int left = validRect.left;
        int top = validRect.top;
        int right = validRect.right;
        int bottom = validRect.bottom;
        int centerY = validRect.centerY();
        float[] pos = {0, 0.125f, 0.25f, 0.375f, 0.5f, 0.625f, 0.75f, 0.875f, 1f};
        LinearGradient lgAll = new LinearGradient(left, centerY, right, centerY, sTheme.getMaskColors(), pos, Shader.TileMode.CLAMP);
        mMaskAllPaint.setShader(lgAll);
        mMaskAllPaint.setStyle(Paint.Style.FILL);

        right = validRect.left + validRect.width() / 7;
        leftMaskRect.set(left, top, right, bottom);
        LinearGradient leftShader = new LinearGradient(left, centerY, right, centerY,
                sTheme.getGradientStartColor(), sTheme.getGradientEndColor(), Shader.TileMode.CLAMP);
        mLeftMaskPaint.setShader(leftShader);
        mLeftMaskPaint.setStyle(Paint.Style.FILL);

        left = validRect.right - validRect.width() / 7;
        right = validRect.right;
        rightMaskRect.set(left, top, right, bottom);
        LinearGradient rightShader = new LinearGradient(right, centerY, left, centerY,
                sTheme.getGradientStartColor(), sTheme.getGradientEndColor(), Shader.TileMode.CLAMP);
        mRightMaskPaint.setShader(rightShader);
        mRightMaskPaint.setStyle(Paint.Style.FILL);
    }

    /** 仅仅更新text draw的x坐标 */
    private void updateTextBasePoint(String text) {
        float textWidth = mTextPaint.measureText(text, 0, text.length());
        mTextPoint.set(mValidDrawBounds.centerX() - textWidth / 2, mTextPoint.y);
    }

    private String getProgressStr(float progress) {
        float flag = progress >= 0 ? 0.5f : -0.5f;
        return String.valueOf((int)(progress + flag) + DU);
    }

    private ArrayList<PointF> mCalibrationsPoint = new ArrayList<PointF>();
    private void calCalibrationsPoints() {
        mCalibrationsPoint.clear();
        int y = mValidDrawBounds.centerY();
        for (Calibration c : mCalibrations) {
            if (c.isFront) {
                mCalibrationsPoint.add(new PointF((float) c.x, y));
            }
        }
    }

    private Path mCalibrationsPath = new Path();
    private void calCalibrationsPath() {
        mCalibrationsPath.reset();
        int height = mValidDrawBounds.height();
        for (Calibration c : mCalibrations) {
            if (c.isFront) {
                double startY = mValidDrawBounds.top + (height - c.l) / 2;
                double endY = startY + c.l;
                mCalibrationsPath.moveTo((float) c.x, (float)startY);
                mCalibrationsPath.lineTo((float) c.x, (float) endY);
            }
        }
    }

    private boolean isFrontSide(Calibration calibration) {
        return isFrontSide(calibration.degree);
    }

    private boolean isFrontSide(double degree) {
        double validDegree = degree % PI_DOU_2;
        validDegree = (validDegree + PI_DOU_2) % PI_DOU_2;
        double max = VISIBLE_HALF_DEGREE;
        boolean isFrontSide = validDegree >= 0 && validDegree <= max;
        isFrontSide |= validDegree >= (PI_DOU_2 - max) && validDegree <= PI_DOU_2;
        return isFrontSide;
    }

    /** return the point of the text being drawn */
    private void getTextBounds(CharSequence text, Paint paint, float[] p) {
        if (p == null || p.length < 3) {
            return;
        }
        float textWidth = paint.measureText(text.toString());
        Paint.FontMetrics fontM = paint.getFontMetrics();
        //baseLine：一行文字的底线。
        //Ascent： 字符顶部到baseLine的距离。
        //Descent： 字符底部到baseLine的距离。
        //Leading： 字符行间距。
        float bottom = fontM.bottom;
        float top = fontM.top;
        p[0] = textWidth;// text width
        p[1] = bottom - top;// text height
        p[2] = top;
        Log.i(TAG, "fontM.ascent="+fontM.ascent + " fontM.bottom="+fontM.bottom+" fontM.descent="+fontM.descent+" fontM.top="+fontM.top);
    }

    private class Calibration {
        /** 圆盘上刻度的长度 */
        private double l;
        /** x轴坐标 */
        private double x;
        /** 偏离圆盘起点的角度(PI) */
        private double degree;
        /** 是否在前面 */
        private boolean isFront;

        void calDegree(float d) {
            degree = (d / 180f * Math.PI) % PI_DOU_2;
        }

        void calX(double r, int offsetX) {
            x = offsetX + r * Math.sin(degree);
        }

        void calL(int ol) {
            l = ol;//ol * Math.abs(Math.cos(degree));
        }
    }

    /** 正三角行游标 */
    private class TriCursor{
        /** 游标的中点坐标 */
        private PointF p;
        /** 三角形游标的边长 */
        private float l;
        /** 三角形的朝向, true 为朝上 */
        private boolean face;

        private Path path;
        private Path srcPath;

        void initPath() {
            if (path == null) {
                path = new Path();
                srcPath = new Path();
            }
            path.reset();
            path.moveTo(p.x - l/2, p.y);
            path.lineTo(p.x + l/2, p.y);
            float y = face ? p.y - l * SIN60 : p.y + l * SIN60;
            path.lineTo(p.x, y);
            srcPath.reset();
            srcPath.addPath(path);
        }

        void offsetX(float dx) {
            if (path != null) {
                path.offset(dx, 0);
            }
        }

        /** view 的中点作为原点，相对原点移动x */
        void transXTo(float x) {
            path.reset();
            path.addPath(srcPath);
            path.offset(x, 0);
        }
    }

    //----------------------------------------------------------------------------------------------

    private OnSeekBarChangeListener mOnSeekBarChangeListener;

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener onSeekBarChangeListener) {
        mOnSeekBarChangeListener = onSeekBarChangeListener;
    }

    /** 获取当前旋转角度 */
    public float getProgress() {
        return mCurrentDegree;
    }

    public void setProgress(float progress) {
        mCurrentDegree = clamp(progress, mMinDegree, mMaxDegree);
        calOffsetByDegree(mCurrentDegree);
        updateTopCursor(mTotalOffsetX);
        updateTextBasePoint(getProgressStr(mCurrentDegree));
        if (mTotalOffsetX == 0) {
            resetCalibrations();
        }
        invalidate();
    }

    private void calOffsetByDegree(float degree) {
        float ratio = degree / mMaxDegree;
        int halfW = mValidDrawBounds.width() / 2;
        mTotalOffsetX = clamp(halfW * ratio, -halfW, halfW);
        mTotalOffsetX = -mTotalOffsetX;
    }

    public interface OnSeekBarChangeListener {
        void onProgressChanged(HorizontalWheelView seekBar, float progress, boolean fromUser);
        void onStartTrackingTouch(HorizontalWheelView seekBar);
        void onStopTrackingTouch(HorizontalWheelView seekBar);
    }

    enum Theme{
        WHITE(Color.WHITE, Color.BLACK & 0xb2000000, Color.BLACK, Color.WHITE, Color.BLACK,
                new int[]{0x7fffffff, 0x75ffffff, 0x5affffff, 0x30ffffff, 0x00ffffff, 0x30ffffff, 0x5affffff, 0x75ffffff, 0x7fffffff},
                0xefffffff, 0x00ffffff),
        BLACK( Color.BLACK, Color.WHITE & 0xb2000000, Color.WHITE, Color.BLACK, Color.WHITE,
                new int[]{0x7f000000, 0x75000000, 0x5a000000, 0x30000000, 0x00000000, 0x30000000, 0x5a000000, 0x75000000, 0x7f000000},
                0xef000000, 0x00000000);

        private int bgColor;
        private int cursorColor;
        private int textColor;
        private int textBgColor;
        private int caliColor;
        private int[] maskColors;
        private int gradientStartColor;
        private int gradientEndColor;

        private Theme(int bgColor, int cursorColor, int textColor, int textBgColor, int caliColor, int[] maskColors,
                      int gradientStartColor, int gradientEndColor) {
            this.bgColor = bgColor;
            this.cursorColor = cursorColor;
            this.textColor = textColor;
            this.textBgColor = textBgColor;
            this.caliColor = caliColor;
            this.maskColors = maskColors;
            this.gradientStartColor = gradientStartColor;
            this.gradientEndColor = gradientEndColor;
        }

        public int getBgColor() {
            return bgColor;
        }

        public int getCursorColor() {
            return cursorColor;
        }

        public int getTextColor() {
            return textColor;
        }

        public int getTextBgColor() {
            return textBgColor;
        }

        public int getCaliColor() {
            return caliColor;
        }

        public int[] getMaskColors() {
            return maskColors;
        }

        public int getGradientStartColor() {
            return gradientStartColor;
        }

        public int getGradientEndColor() {
            return gradientEndColor;
        }
    }
}
