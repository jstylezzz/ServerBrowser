package com.msc.serverbrowser.util.basic;

import static java.io.File.separator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.msc.serverbrowser.logging.Logging;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;

/**
 * Util methods for dealing with downloading and unzipping files.
 *
 * @author oliver
 * @author Marcel
 * @since 01.07.2017
 */
public final class FileUtility
{
	private FileUtility()
	{
		// Constructor to prevent instantiation
	}

	/**
	 * Downloads a file and saves it to the given location.
	 *
	 * @param url
	 *            the url to download from
	 * @param outputPath
	 *            the path where to save the downloaded file
	 * @return the downloaded file
	 * @throws IOException
	 *             if an errors occurs while writing the file or opening the stream
	 */
	public static File downloadFile(final String url, final String outputPath) throws IOException
	{
		try (final ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(url).openStream());
				final FileOutputStream fileOutputStream = new FileOutputStream(outputPath);)
		{
			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
			return new File(outputPath);
		}
	}

	/**
	 * Copies a file overwriting the target if existant
	 *
	 * @param source
	 *            source file
	 * @param target
	 *            target file/location
	 * @throws IOException
	 *             if there was an error during the copy action
	 */
	public static void copyOverwrite(final String source, final String target) throws IOException
	{
		try (FileInputStream fileInputStream = new FileInputStream(source);
				final ReadableByteChannel readableByteChannel = Channels.newChannel(fileInputStream);
				final FileOutputStream fileOutputStream = new FileOutputStream(target);)
		{
			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
		}
	}

	/**
	 * Downloads a file and saves it to the given location.
	 *
	 * @param url
	 *            the url to download from
	 * @param outputPath
	 *            the path where to save the downloaded file
	 * @param progressProperty
	 *            a property that will contain the current download process from 0.0 to 1.0
	 * @param fileLength
	 *            length of the file
	 * @return the downloaded file
	 * @throws IOException
	 *             if an errors occurs while writing the file or opening the stream
	 */
	public static File downloadFile(final URL url, final String outputPath, final DoubleProperty progressProperty,
			final double fileLength)
			throws IOException
	{
		try (final InputStream input = url.openStream();
				final FileOutputStream fileOutputStream = new FileOutputStream(outputPath);)
		{
			final double current = (int) progressProperty.get();
			final byte[] bytes = new byte[10000];
			while (true)
			{
				final double length = input.read(bytes);

				if (length <= 0)
				{
					break;
				}

				Platform.runLater(() ->
				{
					final double additional = length / fileLength * (1.0 - current);
					progressProperty.set(progressProperty.get() + additional);
				});
				fileOutputStream.write(bytes, 0, (int) length);
			}

			return new File(outputPath);
		}
	}

	/**
	 * @param url
	 *            the file url
	 * @return the filesize
	 * @throws IOException
	 *             if there was an error during the web request
	 */
	public static int getOnlineFileSize(final URL url) throws IOException
	{
		HttpURLConnection connection = null;
		try
		{
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("HEAD");
			connection.getInputStream();
			return connection.getContentLength();
		}
		finally
		{
			if (connection != null)
			{
				connection.disconnect();
			}
		}
	}

	/**
	 * Unzips a file.
	 *
	 * @param zipFilePath
	 *            input zip file
	 * @param outputLocation
	 *            zip file output folder
	 * @throws IOException
	 *             if there was an error reading the zip file or writing the unzipped data
	 */
	public static void unzip(final String zipFilePath, final String outputLocation) throws IOException
	{
		// Open the zip file
		try (final ZipFile zipFile = new ZipFile(zipFilePath);)
		{
			final Enumeration<? extends ZipEntry> enu = zipFile.entries();
			while (enu.hasMoreElements())
			{
				final ZipEntry zipEntry = enu.nextElement();

				final String name = zipEntry.getName();
				final long size = zipEntry.getSize();
				final long compressedSize = zipEntry.getCompressedSize();

				Logging.info(String.format("name: %-20s | size: %6d | compressed size: %6d\n", name, size, compressedSize));

				// Do we need to create a directory ?
				final File file = new File(outputLocation + separator + name);
				if (name.endsWith("/"))
				{
					file.mkdirs();
					continue;
				}

				final File parent = file.getParentFile();
				if (parent != null)
				{
					parent.mkdirs();
				}

				// Extract the file
				try (final InputStream inputStream = zipFile.getInputStream(zipEntry);
						final FileOutputStream outputStream = new FileOutputStream(file))
				{
					/*
					 * The buffer is the max amount of bytes kept in RAM during any given time while
					 * unzipping. Since most windows disks are aligned to 4096 or 8192, we use a
					 * multiple of those values for best performance.
					 */
					final byte[] bytes = new byte[8192];
					while (inputStream.available() > 0)
					{
						final int length = inputStream.read(bytes);
						outputStream.write(bytes, 0, length);
					}
				}
			}
		}
	}

	/**
	 * Validates a {@link File} against a SHA-512 checksum.
	 *
	 * @param file
	 *            the file that has to be validated
	 * @param sha512Checksum
	 *            the checksum to validate against
	 * @return true if the file was valid, otherwise false
	 */
	public static boolean validateFile(final File file, final String sha512Checksum)
	{
		try
		{
			return HashingUtility.verifyChecksum(file.getAbsolutePath()).equalsIgnoreCase(sha512Checksum);
		}
		catch (NoSuchAlgorithmException | IOException exception)
		{
			Logging.log(Level.WARNING, "File invalid: " + file.getAbsolutePath(), exception);
			return false;
		}
	}

	/**
	 * Deletes a folder recursively. In case it deletes files on partially, files that had been
	 * deleted already will stay gone.
	 *
	 * @param folder
	 *            will be deleted recursively
	 * @return true if successful, otherwise false
	 */
	public static boolean deleteRecursively(final File folder)
	{
		if (folder.isDirectory())
		{
			for (final File fileOrFolder : folder.listFiles())
			{
				if (!deleteRecursively(fileOrFolder))
				{
					return false;
				}
			}
		}

		return folder.delete();
	}
}
