/*
 * Copyright 2024 Code Intelligence GmbH
 *
 * By downloading, you agree to the Code Intelligence Jazzer Terms and Conditions.
 *
 * The Code Intelligence Jazzer Terms and Conditions are provided in LICENSE-JAZZER.txt
 * located in the root directory of the project.
 */

package com.example;

import com.code_intelligence.jazzer.api.FuzzerSecurityIssueLow;
import com.code_intelligence.jazzer.junit.FuzzTest;

public class SwitchOnIntegersFuzzer {
  static SwitchCoverageHelper cov = new SwitchCoverageHelper(5);

  @FuzzTest
  public void test(int data) {
    if (cov.allBranchesCovered()) {
      throw new FuzzerSecurityIssueLow("All cases visited");
    }

    switch (data) {
      case 1029391:
        cov.coverCase(0);
        break;
      case 10101010:
        cov.coverCase(1);
        break;
      case 20202020:
        cov.coverCase(2);
        break;
      case 303003033:
        cov.coverCase(3);
        break;
      case 409102931:
        cov.coverCase(4);
        break;
      default:
        break;
    }
  }
}
