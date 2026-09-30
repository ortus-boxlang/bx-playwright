/**
 * [BoxLang]
 *
 * Copyright [2026] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.modules.playwright.engine;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Pixel comparison of two PNG screenshots, used by visual regression assertions.
 * <p>
 * Colors are compared with the perceptual YIQ distance used by pixelmatch (and Playwright's
 * {@code toHaveScreenshot}), after blending transparent pixels on white. A pixel differs when its
 * distance is above {@code threshold} (0 = exact, 1 = anything goes, 0.2 is a good default).
 */
public final class ImageDiff {

	/**
	 * The largest possible YIQ distance, from pixelmatch.
	 */
	private static final double	MAX_YIQ_DELTA	= 35215;
	private static final int	DIFF_COLOR		= 0xFFFF0000;
	private static final double	FADE			= 0.1;

	private ImageDiff() {
	}

	/**
	 * The outcome of a comparison.
	 *
	 * @param sameSize   True when both images have the same dimensions
	 * @param width      The width of the actual image
	 * @param height     The height of the actual image
	 * @param diffPixels The number of different pixels
	 * @param diffRatio  The share of different pixels, 0 to 1
	 * @param diffImage  A PNG highlighting the differences in red, or null when the sizes differ
	 */
	public record Result( boolean sameSize, int width, int height, long diffPixels, double diffRatio, byte[] diffImage ) {

		/**
		 * @return A map representation for BoxLang
		 */
		public Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put( "sameSize", sameSize );
			map.put( "width", width );
			map.put( "height", height );
			map.put( "diffPixels", diffPixels );
			map.put( "diffRatio", diffRatio );
			map.put( "diffImage", diffImage );
			return map;
		}

	}

	/**
	 * Compare two PNG images.
	 *
	 * @param expected  The baseline PNG bytes
	 * @param actual    The new PNG bytes
	 * @param threshold The per-pixel color tolerance, 0 to 1
	 *
	 * @return The comparison result
	 */
	public static Result compare( byte[] expected, byte[] actual, double threshold ) {
		BufferedImage	baseline	= read( expected, "baseline" );
		BufferedImage	current		= read( actual, "actual" );
		int				width		= current.getWidth();
		int				height		= current.getHeight();
		if ( baseline.getWidth() != width || baseline.getHeight() != height ) {
			return new Result( false, width, height, ( long ) width * height, 1.0, null );
		}

		double			maxDelta	= MAX_YIQ_DELTA * threshold * threshold;
		BufferedImage	diff		= new BufferedImage( width, height, BufferedImage.TYPE_INT_ARGB );
		long			different	= 0;
		for ( int y = 0; y < height; y++ ) {
			for ( int x = 0; x < width; x++ ) {
				int		a		= baseline.getRGB( x, y );
				int		b		= current.getRGB( x, y );
				double	delta	= a == b ? 0 : colorDelta( a, b );
				if ( delta > maxDelta ) {
					different++;
					diff.setRGB( x, y, DIFF_COLOR );
				} else {
					diff.setRGB( x, y, faded( a ) );
				}
			}
		}
		double ratio = width * height == 0 ? 0 : ( double ) different / ( ( long ) width * height );
		return new Result( true, width, height, different, ratio, write( diff ) );
	}

	/**
	 * The squared YIQ distance between two ARGB colors, after blending on white.
	 */
	static double colorDelta( int first, int second ) {
		double[]	a	= blend( first );
		double[]	b	= blend( second );
		double		dy	= yiqY( a ) - yiqY( b );
		double		di	= yiqI( a ) - yiqI( b );
		double		dq	= yiqQ( a ) - yiqQ( b );
		return 0.5053 * dy * dy + 0.299 * di * di + 0.1957 * dq * dq;
	}

	private static double[] blend( int argb ) {
		double	alpha	= ( ( argb >>> 24 ) & 0xFF ) / 255.0;
		double	r		= ( argb >> 16 ) & 0xFF;
		double	g		= ( argb >> 8 ) & 0xFF;
		double	b		= argb & 0xFF;
		return new double[] {
		    255 + ( r - 255 ) * alpha,
		    255 + ( g - 255 ) * alpha,
		    255 + ( b - 255 ) * alpha
		};
	}

	private static double yiqY( double[] c ) {
		return c[ 0 ] * 0.29889531 + c[ 1 ] * 0.58662247 + c[ 2 ] * 0.11448223;
	}

	private static double yiqI( double[] c ) {
		return c[ 0 ] * 0.59597799 - c[ 1 ] * 0.27417610 - c[ 2 ] * 0.32180189;
	}

	private static double yiqQ( double[] c ) {
		return c[ 0 ] * 0.21147017 - c[ 1 ] * 0.52261711 + c[ 2 ] * 0.31114694;
	}

	/**
	 * A washed out grayscale version of the pixel, so the red differences stand out.
	 */
	private static int faded( int argb ) {
		double[]	c		= blend( argb );
		int			gray	= ( int ) Math.round( 255 + ( yiqY( c ) - 255 ) * FADE );
		return 0xFF000000 | ( gray << 16 ) | ( gray << 8 ) | gray;
	}

	private static BufferedImage read( byte[] png, String label ) {
		try {
			BufferedImage image = png == null ? null : ImageIO.read( new ByteArrayInputStream( png ) );
			if ( image == null ) {
				throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "The " + label + " image is not a readable PNG.",
				    "Delete the baseline to recreate it." );
			}
			return image;
		} catch ( IOException e ) {
			throw PlaywrightErrors.of( PlaywrightErrors.INVALID_OPTION, "Cannot read the " + label + " image: " + e.getMessage(),
			    "Delete the baseline to recreate it.", e );
		}
	}

	private static byte[] write( BufferedImage image ) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write( image, "png", out );
			return out.toByteArray();
		} catch ( IOException e ) {
			throw new IllegalStateException( "Cannot encode the diff image", e );
		}
	}

}
