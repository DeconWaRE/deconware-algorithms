package com.deconware.algorithms;

import static org.junit.Assert.assertEquals;
import junit.framework.Assert;

import org.junit.Test;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.view.Views;

import com.deconware.algorithms.phantom.Phantoms;
import com.deconware.algorithms.psf.PsfGenerator;
import com.deconware.algorithms.psf.PsfGenerator.PsfType;
import com.deconware.algorithms.psf.PsfGenerator.PsfModel;
import com.deconware.algorithms.fft.filters.Convolution;
import com.deconware.algorithms.fft.filters.RichardsonLucyFilter;

import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.Point;

public class DeconvolutionProcessTest 
{
	// test convolution on unsigned byte image
	@Test 
	public void ConvolutionUnsignedByteTest()
	{
		
		System.out.println("********************Convolution Unsigned Byte Test*******************");
		
		long xSize=128;
		long ySize=128;
		long zSize=64;
		
		long[] size=new long[]{xSize, ySize, zSize};
		
		Point center=new Point(3);
		
		center.setPosition(xSize/2,0);
		center.setPosition(ySize/2,1);
		center.setPosition(zSize/2, 2);
		
		float background=0.0f;
		float foreground=100.0f;
		
		// create a planer image factory
		ImgFactory<UnsignedIntType> imgFactory = new PlanarImgFactory<UnsignedIntType>();
											
		// use the image factory to create images
		Img<UnsignedIntType> image1 = imgFactory.create(size, new UnsignedIntType());
		Img<UnsignedIntType> image2 = imgFactory.create(size, new UnsignedIntType());
						
		// add a sphere
		Phantoms.drawSphere(image1, center, 5, foreground);
		Phantoms.drawSphere(image2, center, 5, foreground);
		
		// compute the sum
		double sum1=StaticFunctions.sum(image1);
		double sum2=StaticFunctions.sum(image2);
		
		System.out.println("Sum1: "+sum1);
		System.out.println("Sum2: "+sum2);
		
		Convolution<UnsignedIntType, UnsignedIntType> convolution;
		
		try
		{
			convolution=new Convolution<UnsignedIntType,UnsignedIntType>(image1,
				image2, 
				image1.factory(), 
				image2.factory());
		}
		catch (IncompatibleTypeException ex)
		{
			return;
		}
		
		convolution.process();
		
		Img<UnsignedIntType> out=convolution.getResult();
		
		double outSum=StaticFunctions.sum(out);
		
		System.out.println("out sum: "+outSum);
		System.out.println("in1*in2 "+sum1*sum2);
		
		// output sum should be input1sum*input2sum
		Assert.assertEquals(sum1*sum2, outSum, 0.0001);
		
		// try again using RandomAccessibleIntervals
		RandomAccessibleInterval<UnsignedIntType>  interval1=Views.interval(image1, image1);
		RandomAccessibleInterval<UnsignedIntType>  interval2=Views.interval(image2, image2);
		
		// use the image factory to create images
		Img<UnsignedIntType> output = imgFactory.create(size, new UnsignedIntType());
		RandomAccessibleInterval<UnsignedIntType>  intervalOut=Views.interval(output, output);		
		
		try
		{
			Convolution<UnsignedIntType, UnsignedIntType> convolution2=
				new Convolution<UnsignedIntType, UnsignedIntType>(interval1, interval2, intervalOut);
			
			convolution2.process();
		}
		catch (IncompatibleTypeException ex)
		{
			return;
		}
	
		// this time we shouldn't have to get the result becuase it should be written into the output
		
		// compute the sum
		sum1=StaticFunctions.sum2(interval1);
		sum2=StaticFunctions.sum2(interval2);
		outSum=StaticFunctions.sum2(intervalOut);
				
		// output sum should be input1sum*input2sum
		Assert.assertEquals(sum1*sum2, outSum, 0.0001);
		
		System.out.println("********************Convolution Unsigned Byte Test*******************");	
	}
	
	//@Test
	public void TestDeconvolutionProcess()
	{
		long xSize=128;
		long ySize=128;
		long zSize=64;
		
		long[] size=new long[]{xSize, ySize, zSize};
		
		Point center=new Point(3);
		
		center.setPosition(xSize/2,0);
		center.setPosition(ySize/2,1);
		center.setPosition(zSize/2, 2);
		
		float background=0.0f;
		float foreground=100.0f;
		
		// create a planer image factory
		ImgFactory<FloatType> imgFactory = new PlanarImgFactory<FloatType>();
									
		// use the image factory to create an img
		Img<FloatType> image = imgFactory.create(size, new FloatType());
				
		// create a blank phantom
		StaticFunctions.set(image, background);
		
		// add a sphere
		Phantoms.drawSphere(image, center, 5, foreground);
		
		// compute the sum
		double sum=StaticFunctions.sum(image);
			
		PsfType psfType=PsfType.WIDEFIELD;
		PsfModel psfModel=PsfModel.GIBSON_LANI;
		
		Img<FloatType> psf=PsfGenerator.CallGeneratePsf(new int[]{(int)xSize, (int)ySize, (int)zSize}, new float[]{.1f, .1f, .3f}, 300.0,
				1.4, 1.51, 1.51, 1.51, 1.51, 10.0, 
				psfType, psfModel) ;
		
		// sum of psf
		double psfSum=StaticFunctions.sum(psf);
		
		System.out.println("PSF sum:"+psfSum);
		
		// assert that the sum of PSF energy is 1.0 within an epsilon
		assertEquals(psfSum, 1.0, 0.00001);
		
		Convolution<FloatType, FloatType> convolution;
		
		try
		{
			convolution=new Convolution<FloatType,FloatType>(image,
				psf, 
				image.factory(), 
				psf.factory());
		}
		catch (IncompatibleTypeException ex)
		{
			return;
		}
		
		boolean success=convolution.process();
		
		Img<FloatType> out=convolution.getResult();
		
		double outSum=StaticFunctions.sum(out);
		
		// asser that the sum is till the same after convolution (with an epsilon)
		//assertEquals(outSum, sum, .001);
		
		
		System.out.println("********************Deconvolution Process Test*******************");
	}
}
