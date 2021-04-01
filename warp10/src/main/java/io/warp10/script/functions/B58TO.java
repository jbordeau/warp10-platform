//
//   Copyright 2021  SenX S.A.S.
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

package io.warp10.script.functions;

import java.math.BigInteger;
import java.util.Arrays;

import org.bouncycastle.crypto.digests.SHA256Digest;

import com.google.common.primitives.Bytes;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

/**
 * Encode a String into Base58 or Base58Check
 */
public class B58TO extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

  private static final BigInteger[] TEBAHPLA;

  static {
    TEBAHPLA = new BigInteger[128];
    for (int i = 0; i < ALPHABET.length(); i++) {
      TEBAHPLA[ALPHABET.charAt(i)] = BigInteger.valueOf(i);
    }
  }

  private static final BigInteger FIFTY_EIGHT = new BigInteger("58");

  private final boolean check;

  public B58TO(String name, boolean check) {
    super(name);
    this.check = check;
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();

    byte[] prefix = null;

    if (this.check && !(top instanceof byte[])) {
      throw new WarpScriptException(getName() + " expects a byte array prefix.");
    } else if (this.check) {
      prefix = (byte[]) top;
      top = stack.pop();
    }

    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " operates on a STRING.");
    }

    byte[] decoded = decode((String) top);

    if (check) {
      if (decoded.length < prefix.length + 4) {
        throw new WarpScriptException("Invalid length.");
      }

      for (int i = 0; i < prefix.length; i++) {
        if (i > decoded.length || decoded[i] != prefix[i]) {
          throw new WarpScriptException("Invalid prefix.");
        }
      }

      SHA256Digest digest = new SHA256Digest();
      digest.update(decoded, 0, decoded.length - 4);
      byte[] hash = new byte[digest.getDigestSize()];
      digest.doFinal(hash, 0);
      digest.reset();
      digest.update(hash, 0, hash.length);
      digest.doFinal(hash, 0);

      //
      // Check trailer
      //

      for (int i = 0; i < 4; i++) {
        if (hash[i] != decoded[decoded.length - 4 + i]) {
          throw new WarpScriptException("Invalid checksum.");
        }
      }

      byte[] data = new byte[decoded.length - prefix.length - 4];
      System.arraycopy(decoded, prefix.length, data, 0, data.length);

      decoded = data;
    }

    stack.push(decoded);
    return stack;
  }

  public static byte[] decode(String encoded) throws WarpScriptException {
    //
    // See https://tools.ietf.org/id/draft-msporny-base58-02.html
    //

    int zero_counter = 0;

    while(zero_counter < encoded.length() && '1' == encoded.charAt(zero_counter)) {
      zero_counter++;
    }

    BigInteger n = BigInteger.ZERO;

    for (int i = zero_counter; i < encoded.length(); i++) {
      char c = encoded.charAt(i);
      if (c > 127 || null == TEBAHPLA[c]) {
        throw new WarpScriptException("Invalid input '" + encoded.charAt(i) + "' at position " + i + ".");
      }
      n = n.multiply(FIFTY_EIGHT).add(TEBAHPLA[c]);
    }
    byte[] nbytes = n.toByteArray();
    // If the number is positive but would lead to an hexadecimal notation starting with a byte over 0x7F,
    // the generated byte array starts with a leading 0, this must therefore be stripped.

    if ((byte) 0x00 == nbytes[0]) {
      nbytes = Arrays.copyOfRange(nbytes, 1, nbytes.length);
    }

    return Bytes.concat(new byte[zero_counter], nbytes);
  }
}
