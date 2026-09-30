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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

public class ImageDiffTest {

	/**
	 * Draw a PNG with a solid background and an optional 10x10 square in the top left corner.
	 *
	 * @param width      The image width in pixels
	 * @param height     The image height in pixels
	 * @param background The background color
	 * @param square     The square color, or null for no square
	 *
	 * @return The PNG bytes
	 */
	private static byte[] png( int width, int height, Color background, Color square ) throws IOException {
		BufferedImage	image		= new BufferedImage( width, height, BufferedImage.TYPE_INT_ARGB );
		Graphics2D		graphics	= image.createGraphics();
		graphics.setColor( background );
		graphics.fillRect( 0, 0, width, height );
		if ( square != null ) {
			graphics.setColor( square );
			graphics.fillRect( 0, 0, 10, 10 );
		}
		graphics.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write( image, "png", out );
		return out.toByteArray();
	}

	/**
	 * Comparing an image with itself reports the same size, no differing pixels and a zero ratio.
	 */
	@DisplayName( "Identical images have no differences" )
	@Test
	public void testIdentical() throws IOException {
		byte[]				image	= png( 50, 40, Color.WHITE, Color.BLUE );
		ImageDiff.Result	result	= ImageDiff.compare( image, image, 0.2 );
		assertThat( result.sameSize() ).isTrue();
		assertThat( result.diffPixels() ).isEqualTo( 0 );
		assertThat( result.diffRatio() ).isEqualTo( 0.0 );
	}

	/**
	 * Changed pixels are counted, the ratio reflects them, and the diff image marks them in red and nothing else.
	 */
	@DisplayName( "Changed pixels are counted and drawn in red" )
	@Test
	public void testDifferences() throws IOException {
		ImageDiff.Result	result	= ImageDiff.compare( png( 50, 40, Color.WHITE, null ), png( 50, 40, Color.WHITE, Color.BLACK ), 0.2 );
		BufferedImage		diff	= ImageIO.read( new ByteArrayInputStream( result.diffImage() ) );
		assertThat( result.diffPixels() ).isEqualTo( 100 );
		assertThat( result.diffRatio() ).isWithin( 0.0001 ).of( 100.0 / 2000 );
		assertThat( diff.getRGB( 5, 5 ) ).isEqualTo( 0xFFFF0000 );
		assertThat( diff.getRGB( 40, 30 ) ).isNotEqualTo( 0xFFFF0000 );
	}

	/**
	 * Small color changes are ignored under the threshold and counted with a zero threshold.
	 */
	@DisplayName( "The threshold ignores small color changes" )
	@Test
	public void testThreshold() throws IOException {
		byte[]	a	= png( 20, 20, new Color( 200, 200, 200 ), null );
		byte[]	b	= png( 20, 20, new Color( 203, 203, 203 ), null );
		assertThat( ImageDiff.compare( a, b, 0.2 ).diffPixels() ).isEqualTo( 0 );
		assertThat( ImageDiff.compare( a, b, 0.0 ).diffPixels() ).isEqualTo( 400 );
	}

	/**
	 * Images of different sizes are a full mismatch with no diff image.
	 */
	@DisplayName( "Different sizes are a full mismatch" )
	@Test
	public void testSizeMismatch() throws IOException {
		ImageDiff.Result result = ImageDiff.compare( png( 50, 40, Color.WHITE, null ), png( 60, 40, Color.WHITE, null ), 0.2 );
		assertThat( result.sameSize() ).isFalse();
		assertThat( result.diffRatio() ).isEqualTo( 1.0 );
		assertThat( result.diffImage() ).isNull();
	}

	/**
	 * Bytes that are not images throw a BoxRuntimeException of the invalid option type.
	 */
	@DisplayName( "Unreadable images fail with a typed error" )
	@Test
	public void testUnreadable() {
		BoxRuntimeException error = assertThrows( BoxRuntimeException.class, () -> ImageDiff.compare( new byte[] { 1, 2, 3 }, new byte[] { 1 }, 0.2 ) );
		assertThat( error.getType() ).isEqualTo( PlaywrightErrors.INVALID_OPTION );
	}

}
