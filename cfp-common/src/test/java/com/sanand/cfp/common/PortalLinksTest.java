package com.sanand.cfp.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortalLinksTest {

  private static final String SUB = "11111111-2222-3333-4444-555555555555";
  private static final String RG = "cfp-tutorial-rg";
  private static final String ACCOUNT = "cfp-tutorial-demo";

  @Test
  void accountLinkPointsAtTheArmResource() {
    assertThat(PortalLinks.account(SUB, RG, ACCOUNT))
        .startsWith("https://portal.azure.com/#@/resource/subscriptions/" + SUB)
        .contains("/resourceGroups/" + RG)
        .contains("/providers/Microsoft.DocumentDB/databaseAccounts/" + ACCOUNT)
        .endsWith("/overview");
  }

  @Test
  void dataExplorerAndReplicationBladesDifferOnlyInTheTrailingSegment() {
    assertThat(PortalLinks.dataExplorer(SUB, RG, ACCOUNT)).endsWith("/dataExplorer");
    assertThat(PortalLinks.globalReplication(SUB, RG, ACCOUNT)).endsWith("/globalReplication");
  }

  @Test
  void standaloneExplorerUrlEncodesTheAccountName() {
    assertThat(PortalLinks.standaloneDataExplorer("my account"))
        .isEqualTo("https://cosmos.azure.com/?accountName=my+account");
  }
}
