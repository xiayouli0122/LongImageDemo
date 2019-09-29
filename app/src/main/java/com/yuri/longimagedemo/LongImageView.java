package com.yuri.longimagedemo;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import java.io.IOException;
import java.io.InputStream;

/**
 * 加载长图，这里是横图加载，暂未实现双机放大，手势放大缩小
 */
public class LongImageView extends View implements GestureDetector.OnGestureListener, View.OnTouchListener {

    private static final String TAG = LongImageView.class.getSimpleName();
    private Rect mRect;

    private BitmapFactory.Options mOptions;

    private GestureDetector mGestureDetector;

    private Scroller mScroller;
    private int mImageWidth;
    private int mImageHeight;
    private BitmapRegionDecoder mDecoder;
    private int mViewWidth;
    private int mViewHeight;
    private float mScale;
    private Bitmap mBitmap;

    public LongImageView(Context context) {
        super(context);
        initView(context);
    }

    public LongImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public LongImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        //1.设置所需要的一些成员变量
        mRect = new Rect();

        //内存复用所需要的
        mOptions = new BitmapFactory.Options();
        //手势识别
        mGestureDetector = new GestureDetector(context, this);

        mScroller = new Scroller(context);

        setOnTouchListener(this);
    }

    //2.
    public void setImage(InputStream is) {
        //设置读取图片不加载到内存，只读取图片的属性
        mOptions.inJustDecodeBounds = true;
        //这里就不需要保存返回值bitmap，只是拿到options属性
        BitmapFactory.decodeStream(is, null, mOptions);
        mImageWidth = mOptions.outWidth;
        mImageHeight = mOptions.outHeight;
        Log.d(TAG, "setImage: image.width:" + mImageWidth + ",height:" + mImageHeight);

        //设置可变，内存复用
        mOptions.inMutable = true;
        mOptions.inPreferredConfig = Bitmap.Config.RGB_565;


        //设置为false，加载图片到内存中
        mOptions.inJustDecodeBounds = false;

        //区域解码器
        try {
            mDecoder = BitmapRegionDecoder.newInstance(is, false);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //开始渲染
        requestLayout();
    }

    //3.读取view的宽高，测量要加载的图片要缩放成什么样子
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mViewWidth = getMeasuredWidth();
        mViewHeight = getMeasuredHeight();
        Log.d(TAG, "onMeasure: view.width:" + mViewWidth + ",height:" + mViewHeight);

        //确定加载图片的区域
        mRect.left = 0;
        mRect.top = 0;
        mRect.right = mImageWidth;
        //计算缩放比例
        mScale = mViewWidth / (float) mImageWidth;
        //初始，加载区域的底部就是加载控件的高度/缩放比例
        mRect.bottom = (int) (mViewHeight / mScale);
        Log.d(TAG, "onMeasure: scale:" + mScale);
        Log.d(TAG, "onMeasure: bottom:" + mRect.bottom + ",right:" + mRect.right);
    }

    //4. 画出具体的内容
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDecoder == null) {
            return;
        }

        //真正的内存复用
        //复用的bitmap必须跟即将解码的bitmap尺寸大小一样
        mOptions.inBitmap = mBitmap;
        //指定解码区域
        mBitmap = mDecoder.decodeRegion(mRect, mOptions);

        //因为图片的尺寸比例未必跟控件的宽高一致，所以治理我们需要将图片等比缩放到适应控件的宽高
        Matrix matrix = new Matrix();
        matrix.setScale(mScale, mScale);
        canvas.drawBitmap(mBitmap, matrix, null);
    }

    //5.处理触摸事件
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //完全交给手势处理类
        return mGestureDetector.onTouchEvent(event);
    }

    //6.手势处理 按下事件
    @Override
    public boolean onDown(MotionEvent e) {
        //如果正在滑动或者移动，强行停止
        if (!mScroller.isFinished()) {
            mScroller.forceFinished(true);
        }
        return true;
    }

    //7.处理滑动事件

    /**
     * @param e1        开始事件，手指按下去，获取坐标
     * @param e2        获取当前事件
     * @param distanceX x移动距离
     * @param distanceY y移动距离
     * @return
     */
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //上下移动的时候，需要改变现实区域
        mRect.offset(0, (int) distanceY);
        //移动时处理到底的处理逻辑
        if (mRect.bottom > mImageHeight) {
            mRect.bottom = mImageHeight;
            mRect.top = (int) (mImageWidth - mViewHeight / mScale);
        }

        //到顶的处理逻辑
        if (mRect.top < 0) {
            mRect.top = 0;
            mRect.bottom = (int) (mViewHeight / mScale);
        }

        Log.d(TAG, "onScroll: top:" + mRect.top + ",bottom:" + mRect.bottom);

        //重绘
        invalidate();
        return false;
    }

    //8.处理惯性问题
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        //这里将velocityY取反才是我们正常的向上向下
        mScroller.fling(0, mRect.top, 0, (int) -velocityY, 0, 0, 0, (int) (mImageHeight - mViewHeight / mScale));
        return false;
    }

    //9.处理计算结果
    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.isFinished()) {
            return;
        }

        if (mScroller.computeScrollOffset()) {
            mRect.top = mScroller.getCurrY();
            mRect.bottom = (int) (mRect.top + mViewHeight / mScale);
            invalidate();
        }

    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }


    @Override
    public void onLongPress(MotionEvent e) {

    }
}
