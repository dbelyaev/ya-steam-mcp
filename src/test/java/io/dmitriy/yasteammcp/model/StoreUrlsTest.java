package io.dmitriy.yasteammcp.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoreUrlsTest {

	@Test
	void app_withAppid_shouldBuildStorePageUrl() {
		assertThat(StoreUrls.app(730)).isEqualTo("https://store.steampowered.com/app/730/");
	}

}
