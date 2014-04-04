/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trigonic.jrobotx;

import static com.trigonic.jrobotx.Constants.HTTP;
import static com.trigonic.jrobotx.Constants.HTTPS;
import static com.trigonic.jrobotx.Constants.ROBOTS_TXT;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trigonic.jrobotx.util.DefaultURLInputStreamFactory;
import com.trigonic.jrobotx.util.URLInputStreamFactory;

public class RobotExclusion {
	private static final Logger LOG = LoggerFactory.getLogger(RobotExclusion.class);
	
	private static final Set<String> SUPPORTED_PROTOCOLS = new HashSet<String>(Arrays.asList(HTTP, HTTPS));
	
	// Milliseconds after which the cache expires
	private static final long EXPIRE_CACHE = 604800000; // 1 week
	
	private URLInputStreamFactory urlInputStreamFactory;

	/**
	 * Directory in which to store cached robots.txt files.
	 */
	private File cacheDir;
	
	public RobotExclusion(URLInputStreamFactory urlInputStreamFactory) {
		this.urlInputStreamFactory = urlInputStreamFactory;
	}
	
	public RobotExclusion() {
		this(new DefaultURLInputStreamFactory());
	}

	/** Instantiate the robot parser, using the specified directory for robots.txt caching.
	 * 
	 * @param cacheDir
	 */
	public RobotExclusion(File cacheDir) {
		this();
		
		if(!cacheDir.exists()) cacheDir.mkdirs();
		this.cacheDir = cacheDir;
	}
	
	/**
	 * Get a robot exclusion {@link RecordIterator} for the server in the specified {@link URL}, or null if none is
	 * available. If the protocol is not supported--that is, not HTTP-based--null is returned.
	 */
	public RecordIterator get(URL url) {
		RecordIterator recordIter = null;
		
		if (!SUPPORTED_PROTOCOLS.contains(url.getProtocol().toLowerCase())) {
			return null;
		}

		try {
			// TODO: this should support error conditions as described in the protocol draft
			URL robotsUrl = new URL(url, ROBOTS_TXT);
			
			// Check the cache
			StringBuilder sb = new StringBuilder();
			sb.append(robotsUrl.getProtocol()).append("/").append(robotsUrl.getHost());
			if(robotsUrl.getPort() > 0) sb.append("/").append(robotsUrl.getPort());
			sb.append(ROBOTS_TXT);
			
			// Use caching if it is enabled
			if(cacheDir != null)
			{
				File cache = new File(cacheDir, sb.toString());
				
				// If the file is not cached, or the cache has expired, then cache it
				try
				{
					if(!cache.exists() || System.currentTimeMillis() - cache.lastModified() > EXPIRE_CACHE)
					{
						BufferedReader in = null;
						PrintWriter out = null;
						try
						{
							cache.getParentFile().mkdirs();
							out = new PrintWriter(new FileWriter(cache));
							in = new BufferedReader(new InputStreamReader(urlInputStreamFactory.openStream(robotsUrl)));
							String line = in.readLine();
							while(line != null)
							{
								out.println(line);
								line = in.readLine();
							}
						}
						finally
						{
							if(out != null) out.close();
							if(in != null) in.close();
						}
					}
					
					if(cache.exists())
					{
						recordIter = new RecordIterator(new FileInputStream(cache));
					}
				}
				catch(IOException e)
				{
					LOG.error("Exception caching " + robotsUrl, e);
				}
			}
			// Fall back, just try and get the file
			if(recordIter == null) recordIter = new RecordIterator(urlInputStreamFactory.openStream(robotsUrl));
		} catch (IOException e) {
			LOG.info("Failed to fetch " + url, e);
		}

		return recordIter;
	}

	/**
	 * Get a robot exclusion {@link Record} for the specified {@link URL}, or null if none is available.  This uses {@link #get(URL)}
	 * and iterates through the {@link RecordIterator} to find a matching {@link Record}.
	 */
	public Record get(URL url, String userAgentString) {
		Record result = null;
		RecordIterator recordIter = get(url);
		if (recordIter != null) {
			while (recordIter.hasNext()) {
				Record record = recordIter.next();
				if (record.matches(userAgentString)) {
					result = record;
					break;
				}
			}
			if (result == null) {
				result = recordIter.getDefaultRecord();
			}
		}
		return result;
	}

	/**
	 * Determine whether the specified {@link URL} is allowed for the specified user agent string.  This uses {@link #get(URL, String)}
	 * and returns whether the matching record allows the {@link URL#getPath() path} specified in the URL.  
	 */
	public boolean allows(URL url, String userAgentString) {
	    // shortcut - /robots.txt might not exist, but it must be allowed
        if (Record.ruleMatches(ROBOTS_TXT, url.getFile())) {
            return true;
        }
            
		Record record = get(url, userAgentString);
		return record == null || record.allows(url.getPath());
	}

	/**
	 * 
	 * @param url 
	 * @param userAgent
	 * @return
	 */
	public int getCrawlDelay(URL url, String userAgent)
	{
		Record record = get(url, userAgent);
		if(record == null) return 0;
		return record.getCrawlDelay();
	}
}
