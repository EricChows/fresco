/*
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */
package com.facebook.fresco.animation.bitmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.facebook.common.references.CloseableReference;
import com.facebook.common.references.ResourceReleaser;
import com.facebook.fresco.animation.backend.AnimationBackend;
import com.facebook.fresco.animation.backend.AnimationInformation;
import com.facebook.imagepipeline.bitmaps.PlatformBitmapFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.robolectric.RobolectricTestRunner;

/**
 * Tests {@link BitmapAnimationBackend}
 */
@RunWith(RobolectricTestRunner.class)
public class BitmapAnimationBackendTest {

  @Mock public PlatformBitmapFactory mPlatformBitmapFactory;
  @Mock public BitmapFrameCache mBitmapFrameCache;
  @Mock public AnimationInformation mAnimationInformation;
  @Mock public BitmapFrameRenderer mBitmapFrameRenderer;
  @Mock public Rect mBounds;
  @Mock public Drawable mParentDrawable;
  @Mock public Canvas mCanvas;
  @Mock public Bitmap mBitmap;
  @Mock public ResourceReleaser<Bitmap> mBitmapResourceReleaser;
  @Mock public BitmapAnimationBackend.FrameListener mFrameListener;

  @Captor public ArgumentCaptor<CloseableReference<Bitmap>> mCapturedBitmapReference;

  private CloseableReference<Bitmap> mBitmapRefererence;

  private BitmapAnimationBackend mBitmapAnimationBackend;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    mBitmapRefererence = CloseableReference.of(mBitmap, mBitmapResourceReleaser);
    mBitmapAnimationBackend = new BitmapAnimationBackend(
        mPlatformBitmapFactory,
        mBitmapFrameCache,
        mAnimationInformation,
        mBitmapFrameRenderer);
    mBitmapAnimationBackend.setFrameListener(mFrameListener);
  }

  @Test
  public void testSetBounds() {
    mBitmapAnimationBackend.setBounds(mBounds);
    verify(mBitmapFrameRenderer).setBounds(mBounds);
  }

  @Test
  public void testGetIntrinsicDimensions() {
    when(mBounds.width()).thenReturn(123);
    when(mBounds.height()).thenReturn(234);
    when(mBitmapFrameRenderer.getIntrinsicWidth())
        .thenReturn(AnimationBackend.INTRINSIC_DIMENSION_UNSET);
    when(mBitmapFrameRenderer.getIntrinsicHeight())
        .thenReturn(AnimationBackend.INTRINSIC_DIMENSION_UNSET);

    mBitmapAnimationBackend.setBounds(mBounds);

    assertThat(mBitmapAnimationBackend.getIntrinsicWidth()).isEqualTo(123);
    assertThat(mBitmapAnimationBackend.getIntrinsicHeight()).isEqualTo(234);
  }

  @Test
  public void testGetFrameCount() {
    when(mAnimationInformation.getFrameCount()).thenReturn(123);
    assertThat(mBitmapAnimationBackend.getFrameCount()).isEqualTo(123);
  }

  @Test
  public void testGetLoopCount() {
    when(mAnimationInformation.getLoopCount()).thenReturn(AnimationInformation.LOOP_COUNT_INFINITE);
    assertThat(mBitmapAnimationBackend.getLoopCount())
        .isEqualTo(AnimationInformation.LOOP_COUNT_INFINITE);

    when(mAnimationInformation.getLoopCount()).thenReturn(123);
    assertThat(mBitmapAnimationBackend.getLoopCount()).isEqualTo(123);
  }

  @Test
  public void testGetFrameDuration() {
    when(mAnimationInformation.getFrameDurationMs(1)).thenReturn(50);
    when(mAnimationInformation.getFrameDurationMs(2)).thenReturn(100);

    assertThat(mBitmapAnimationBackend.getFrameDurationMs(1))
        .isEqualTo(50);
    assertThat(mBitmapAnimationBackend.getFrameDurationMs(2))
        .isEqualTo(100);
  }

  @Test
  public void testDrawCachedBitmap() {
    when(mBitmapFrameCache.getCachedFrame(anyInt()))
        .thenReturn(mBitmapRefererence);

    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 1);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 1);
    verify(mBitmapFrameCache).getCachedFrame(1);
    verify(mCanvas).drawBitmap(eq(mBitmap), eq(0f), eq(0f), any(Paint.class));
    verifyListenersCalled(1, mBitmap, BitmapAnimationBackend.FRAME_TYPE_CACHED);
    assertReferencesClosed();
  }

  @Test
  public void testDrawReusedBitmap() {
    when(mBitmapFrameCache.getBitmapToReuseForFrame(anyInt(), anyInt(), anyInt()))
        .thenReturn(mBitmapRefererence);
    when(mBitmapFrameRenderer.renderFrame(anyInt(), any(Bitmap.class))).thenReturn(true);

    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 1);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 1);
    verify(mBitmapFrameCache).getCachedFrame(1);
    verify(mBitmapFrameCache).getBitmapToReuseForFrame(1, 0, 0);
    verify(mBitmapFrameRenderer).renderFrame(1, mBitmap);
    verify(mCanvas).drawBitmap(eq(mBitmap), eq(0f), eq(0f), any(Paint.class));
    verifyListenersCalled(1, mBitmap, BitmapAnimationBackend.FRAME_TYPE_REUSED);
    assertReferencesClosed();
  }

  @Test
  public void testDrawNewBitmap() {
    when(mPlatformBitmapFactory.createBitmap(anyInt(), anyInt(), any(Bitmap.Config.class)))
        .thenReturn(mBitmapRefererence);
    when(mBitmapFrameRenderer.renderFrame(anyInt(), any(Bitmap.class))).thenReturn(true);

    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 2);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 2);
    verify(mBitmapFrameCache).getCachedFrame(2);
    verify(mBitmapFrameCache).getBitmapToReuseForFrame(2, 0, 0);
    verify(mPlatformBitmapFactory).createBitmap(0, 0, Bitmap.Config.ARGB_8888);
    verify(mBitmapFrameRenderer).renderFrame(2, mBitmap);
    verify(mCanvas).drawBitmap(eq(mBitmap), eq(0f), eq(0f), any(Paint.class));
    verifyListenersCalled(2, mBitmap, BitmapAnimationBackend.FRAME_TYPE_CREATED);
    assertReferencesClosed();
  }

  @Test
  public void testDrawNewBitmapWithBounds() {
    int width = 160;
    int height = 90;
    when(mBounds.width()).thenReturn(width);
    when(mBounds.height()).thenReturn(height);

    when(mPlatformBitmapFactory.createBitmap(anyInt(), anyInt(), any(Bitmap.Config.class)))
        .thenReturn(mBitmapRefererence);
    when(mBitmapFrameRenderer.renderFrame(anyInt(), any(Bitmap.class))).thenReturn(true);
    when(mBitmapFrameRenderer.getIntrinsicWidth())
        .thenReturn(AnimationBackend.INTRINSIC_DIMENSION_UNSET);
    when(mBitmapFrameRenderer.getIntrinsicHeight())
        .thenReturn(AnimationBackend.INTRINSIC_DIMENSION_UNSET);

    mBitmapAnimationBackend.setBounds(mBounds);
    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 2);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 2);
    verify(mBitmapFrameCache).getCachedFrame(2);
    verify(mBitmapFrameCache).getBitmapToReuseForFrame(2, width, height);
    verify(mPlatformBitmapFactory).createBitmap(width, height, Bitmap.Config.ARGB_8888);
    verify(mBitmapFrameRenderer).renderFrame(2, mBitmap);
    verify(mCanvas).drawBitmap(eq(mBitmap), isNull(Rect.class), eq(mBounds), any(Paint.class));
    verifyListenersCalled(2, mBitmap, BitmapAnimationBackend.FRAME_TYPE_CREATED);
    assertReferencesClosed();
  }

  @Test
  public void testDrawFallbackBitmapWhenCreateBitmapNotWorking() {
    when(mBitmapFrameCache.getFallbackFrame(anyInt())).thenReturn(mBitmapRefererence);
    when(mBitmapFrameRenderer.renderFrame(anyInt(), any(Bitmap.class))).thenReturn(true);

    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 3);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 3);
    verify(mBitmapFrameCache).getCachedFrame(3);
    verify(mBitmapFrameCache).getBitmapToReuseForFrame(3, 0, 0);
    verify(mPlatformBitmapFactory).createBitmap(0, 0, Bitmap.Config.ARGB_8888);
    verify(mBitmapFrameCache).getFallbackFrame(3);
    verify(mCanvas).drawBitmap(eq(mBitmap), eq(0f), eq(0f), any(Paint.class));
    verifyListenersCalled(3, mBitmap, BitmapAnimationBackend.FRAME_TYPE_FALLBACK);
    assertReferencesClosed();
  }

  @Test
  public void testDrawFallbackBitmapWhenRenderFrameNotWorking() {
    when(mBitmapFrameCache.getFallbackFrame(anyInt())).thenReturn(mBitmapRefererence);

    // Return a different bitmap for PlatformBitmapFactory
    CloseableReference<Bitmap> temporaryBitmap =
        CloseableReference.of(mBitmap, mBitmapResourceReleaser);
    when(mPlatformBitmapFactory.createBitmap(anyInt(), anyInt(), any(Bitmap.Config.class)))
        .thenReturn(temporaryBitmap);
    when(mBitmapFrameRenderer.renderFrame(anyInt(), any(Bitmap.class))).thenReturn(false);

    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 3);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 3);
    verify(mBitmapFrameCache).getCachedFrame(3);
    verify(mBitmapFrameCache).getBitmapToReuseForFrame(3, 0, 0);
    verify(mPlatformBitmapFactory).createBitmap(0, 0, Bitmap.Config.ARGB_8888);
    // Verify that the bitmap has been closed
    assertThat(temporaryBitmap.isValid()).isFalse();
    verify(mBitmapFrameCache).getFallbackFrame(3);
    verify(mCanvas).drawBitmap(eq(mBitmap), eq(0f), eq(0f), any(Paint.class));
    verifyListenersCalled(3, mBitmap, BitmapAnimationBackend.FRAME_TYPE_FALLBACK);
    assertReferencesClosed();
  }

  @Test
  public void testDrawNoFrame() {
    mBitmapAnimationBackend.drawFrame(mParentDrawable, mCanvas, 4);

    verify(mFrameListener).onDrawFrameStart(mBitmapAnimationBackend, 4);
    verify(mBitmapFrameCache).getCachedFrame(4);
    verify(mBitmapFrameCache).getBitmapToReuseForFrame(4, 0, 0);
    verify(mPlatformBitmapFactory).createBitmap(0, 0, Bitmap.Config.ARGB_8888);
    verify(mBitmapFrameCache).getFallbackFrame(4);
    verifyNoMoreInteractions(mCanvas, mBitmapFrameCache);
    verify(mFrameListener)
        .onFrameDropped(mBitmapAnimationBackend, 4);
  }

  private void verifyListenersCalled(
      int frameNumber,
      Bitmap bitmap,
      @BitmapAnimationBackend.FrameType int frameType) {
    // Verify cache callback
    verify(mBitmapFrameCache).onFrameRendered(
        eq(frameNumber),
        mCapturedBitmapReference.capture(),
        eq(frameType));
    assertThat(mCapturedBitmapReference.getValue()).isEqualTo(mBitmapRefererence);

    // Verify frame listener
    verify(mFrameListener).onFrameDrawn(mBitmapAnimationBackend, frameNumber, frameType);
  }

  private void assertReferencesClosed() {
    assertThat(mBitmapRefererence.isValid()).isFalse();
  }
}