//
//   Copyright 2020  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.json;

import java.io.IOException;
import java.io.Writer;

/**
 * A wrapper for Writers to limit the number of written chars.
 * When the number of chars that should be written exceed the given limit, a WriterBoundReachedException is thrown.
 */
public class BoundedWriter extends Writer {

  public class WriterBoundReachedException extends IOException {

    public WriterBoundReachedException(String message) {
      super(message);
    }

  }

  protected final Writer writer;
  protected final int maxWrittenChars;
  protected int currentWrittenChars;

  public BoundedWriter(Writer writer, int maxAppendedChars) {
    this.writer = writer;
    this.maxWrittenChars = maxAppendedChars;
    this.currentWrittenChars = 0;
  }

  @Override
  public void write(char[] chars, int start, int end) throws IOException {
    if (end - start + currentWrittenChars - maxWrittenChars > 0) {
      throw new WriterBoundReachedException("Cannot write, maximum number of characters written :" + this.maxWrittenChars);
    }
    this.currentWrittenChars += end - start;
    writer.write(chars, start, end);
  }

  @Override
  public void flush() throws IOException {
    writer.flush();
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
