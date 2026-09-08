<template>
  <div v-if="!authReady" class="login-page">
    <div class="login-stage"><div class="login-box">{{ streamT('common.loading') }}</div></div>
  </div>

  <div v-else-if="!token && !userToken" class="login-page">
    <div class="login-bg-orbs" aria-hidden="true">
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>
      <div class="bg-orb orb-3"></div>
    </div>
    <div class="login-bg-stars" aria-hidden="true">
      <i v-for="n in 30" :key="n" class="bg-star" :style="getStarStyle(n)"></i>
    </div>
    <div class="login-bg-grid" aria-hidden="true"></div>
    <div class="login-stage">
      <div class="login-art" aria-hidden="true">
        <div class="signal-ring"></div>
        <div class="signal-card">
          <span>{{ publicStats.dramaCount || '3' }}</span>
          <small>{{ streamT('auth.titles') }}</small>
        </div>
        <div class="signal-card second">
          <span>{{ publicStats.episodeCount || '134' }}</span>
          <small>{{ streamT('auth.episodes') }}</small>
        </div>
      </div>

      <el-form class="login-box" :model="loginForm" label-position="top" @submit.prevent="submitAuth">
        <div class="login-mark">SD</div>
        <h1>{{ streamT('auth.loginTitle') }}</h1>
        <p>{{ loginRole === 'admin' ? streamT('auth.adminLoginSubtitle') : streamT('auth.userLoginSubtitle') }}</p>
        <el-segmented v-if="authMode === 'login'" v-model="loginRole" class="auth-role" :options="loginRoleOptions" />
        <el-segmented v-model="authMode" class="auth-mode" :options="authModeOptions" />
        <el-form-item :label="streamT('auth.account')" required>
          <el-input v-model.trim="loginForm.account" autocomplete="username" size="large" />
        </el-form-item>
        <el-form-item :label="streamT('auth.password')" required>
          <el-input v-model="loginForm.password" type="password" autocomplete="current-password" show-password size="large" />
        </el-form-item>
        <el-form-item v-if="authMode === 'register'" :label="streamT('auth.nickname')">
          <el-input v-model.trim="loginForm.nickname" autocomplete="nickname" size="large" />
        </el-form-item>
        <el-form-item :label="streamT('auth.captcha')" required>
          <div class="captcha-row">
            <el-input v-model.trim="loginForm.captchaCode" autocomplete="off" size="large" />
            <button class="captcha-image" type="button" @click="loadAuthCaptcha" :disabled="captchaLoading">
              <img v-if="captchaImage" :src="captchaImage" :alt="streamT('auth.captcha')" />
              <span v-else-if="captchaLoading">{{ streamT('common.loading') }}</span>
              <span v-else class="captcha-retry">{{ streamT('auth.clickRefresh') }}</span>
            </button>
          </div>
        </el-form-item>
        <el-button type="primary" class="full" size="large" :loading="authLoading" @click="submitAuth">
          {{ authMode === 'login' ? streamT('auth.signIn') : streamT('auth.createAccount') }}
        </el-button>
        <div v-if="authError" class="auth-error">{{ authError }}</div>
        <button class="link-button" type="button" @click="toggleAuthMode">
          {{ authMode === 'login' ? streamT('auth.newAccount') : streamT('auth.existingAccount') }}
        </button>
      </el-form>
    </div>
  </div>

  <UserApp v-else-if="userToken" @logout="logoutUser" />

  <el-container v-else class="shell">
    <el-aside width="240px">
      <div class="brand">
        <span class="brand-mark">SD</span>
        <span>{{ t('appName') }}</span>
      </div>
      <el-menu :default-active="view" :default-openeds="openedMenus" @select="selectView">
        <el-menu-item v-if="menuVisibility.dashboard" index="dashboard">{{ t('dashboard') }}</el-menu-item>
        <el-sub-menu v-if="menuVisibility.contentManagement" index="contentManagement">
          <template #title>{{ t('contentManagement') }}</template>
          <el-menu-item v-if="menuVisibility.drama" index="drama">{{ t('dramaLibrary') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.episode" index="episode">{{ t('episodeManagement') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.category" index="category">{{ t('categoryConfig') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.comment" index="comment">{{ t('commentManagement') }}</el-menu-item>
        </el-sub-menu>
        <el-menu-item v-if="menuVisibility.recommendations" index="recommendations">{{ t('recommendationsManagement') }}</el-menu-item>
        <el-sub-menu v-if="menuVisibility.userOperations" index="userOperations">
          <template #title>{{ t('userOperations') }}</template>
          <el-menu-item v-if="menuVisibility.users" index="users">{{ t('userPool') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.feedback" index="feedback">{{ t('feedbackManagement') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.membership" index="membership">{{ t('membershipManagement') }}</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="menuVisibility.tradeCenter" index="tradeCenter">
          <template #title>{{ t('tradeCenter') }}</template>
          <el-menu-item v-if="menuVisibility.orders" index="orders">{{ t('orderManagement') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.package" index="package">{{ t('packageManagement') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.shop" index="shop">{{ t('shopManagement') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.autoRenewal" index="autoRenewal">{{ t('autoRenewalManagement') }}</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="menuVisibility.dataCenter" index="dataCenter">
          <template #title>{{ t('dataCenter') }}</template>
          <el-menu-item v-if="menuVisibility.analytics" index="analytics">{{ t('contentAnalytics') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.pointRecords" index="pointRecords">{{ t('pointRecords') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.membershipStats" index="membershipStats">{{ t('membershipStats') }}</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="menuVisibility.systemSettings" index="systemSettings">
          <template #title>{{ t('systemSettings') }}</template>
          <el-menu-item v-if="menuVisibility.roles" index="roles">{{ t('roles') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.logs" index="logs">{{ t('logs') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.announcement" index="announcement">{{ t('announcementManagement') }}</el-menu-item>
          <el-menu-item v-if="menuVisibility.currencyRate" index="currencyRate">{{ t('currencyRateManagement') }}</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="side-footer">
        <button class="profile-btn" @click="view = 'profile'">
          <el-icon><User /></el-icon>
          个人中心
        </button>
        <button class="logout-btn" @click="logout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </button>
      </div>
    </el-aside>

    <el-main>
      <AdminProfilePage v-if="view === 'profile'" />
      <CurrencyRatePage v-else-if="view === 'currencyRate'" />
      <AdminPointProductPage v-else-if="view === 'package'" :admin-t="adminT" :field="field" :format-number="formatNumber" :format-money="formatMoney" title="套餐管理" eyebrow="交易中心" />
      <AdminMembershipPage v-else-if="view === 'membership'" :admin-t="adminT" :field="field" :format-number="formatNumber" :format-money="formatMoney" :format-date-time="formatDateTime" />
      <AdminShopPage v-else-if="view === 'shop'" :admin-t="adminT" :field="field" :format-number="formatNumber" :format-date-time="formatDateTime" />
      <AdminPointRecordPage v-else-if="view === 'pointRecords'" :admin-t="adminT" :field="field" :format-number="formatNumber" :format-date-time="formatDateTime" />
      <AdminAutoRenewalPage v-else-if="view === 'autoRenewal'" :admin-t="adminT" :field="field" :format-date-time="formatDateTime" />
      <AdminMembershipStats v-else-if="view === 'membershipStats'" :admin-t="adminT" :field="field" :format-number="formatNumber" />
      <AdminCommentPage v-else-if="view === 'comment'" :admin-t="adminT" :field="field" :format-date-time="formatDateTime" />
      <template v-else>
      <header class="page-head">
        <div>
          <p class="eyebrow">{{ pageEyebrow }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="head-actions">
          <div class="stats">
          <div class="stat">
            <span>{{ categoryFilterOptionCount }}</span>
            <label>{{ t('filterOptions') }}</label>
          </div>
          <div class="stat">
            <span>{{ dramas.length }}</span>
            <label>{{ t('titles') }}</label>
          </div>
          <div class="stat">
            <span>{{ dashboardData.kpis?.totalEpisodes || episodes.length }}</span>
            <label>{{ t('episodes') }}</label>
          </div>
          </div>
        </div>
      </header>

      <section v-if="view === 'category'" class="asset-section">
        <div class="toolbar">
          <div>
            <p class="eyebrow">{{ t('contentAssets') }}</p>
            <h2>{{ t('categoryConfig') }}</h2>
          </div>
          <el-button type="primary" @click="editCategory(newCategoryFilterDraft())">{{ t('newCategoryOption') }}</el-button>
        </div>
        <div class="system-category-board">
          <div v-if="contentTypeFilterOptions.length" class="system-type-grid">
            <article
              v-for="option in contentTypeFilterOptions"
              :key="option.id || option.key"
              class="system-type-card"
              :class="{ disabled: Number(option.status ?? 1) !== 1 }"
              @click="editCategory(toCategoryFilterDraft(filterGroup('contentType'), option))"
            >
              <span>{{ t('contentType') }}</span>
              <strong>{{ filterOptionLabel(option) }}</strong>
              <em>{{ Number(option.status ?? 1) === 1 ? t('enabled') : t('disabled') }}</em>
              <div class="system-card-actions" @click.stop>
                <el-button size="small" text @click="editCategory(toCategoryFilterDraft(filterGroup('contentType'), option))">{{ t('edit') }}</el-button>
                <el-button size="small" text type="danger" @click="removeCategory(toCategoryFilterDraft(filterGroup('contentType'), option))">{{ t('delete') }}</el-button>
              </div>
            </article>
          </div>
          <div class="system-category-grid">
            <article v-for="group in visibleCategoryFilterGroups" :key="group.key" class="system-group-card">
              <header>
                <div>
                  <strong>{{ filterGroupLabel(group.key) }}</strong>
                  <span>{{ enabledCountText(group) }}</span>
                </div>
                <el-button size="small" @click="editCategory(newCategoryFilterDraft(group))">{{ t('newCategoryOption') }}</el-button>
              </header>
              <div class="system-option-list">
                <div
                  v-for="option in group.options"
                  :key="option.id || option.key"
                  class="system-option-item"
                  :class="{ disabled: Number(option.status ?? 1) !== 1 }"
                >
                  <span>{{ filterOptionLabel(option) }}</span>
                  <el-tag v-if="Number(option.status ?? 1) !== 1" type="info" size="small">{{ t('disabled') }}</el-tag>
                  <div>
                    <el-button size="small" text @click="editCategory(toCategoryFilterDraft(group, option))">{{ t('edit') }}</el-button>
                    <el-button size="small" text type="danger" @click="removeCategory(toCategoryFilterDraft(group, option))">{{ t('delete') }}</el-button>
                  </div>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section v-if="view === 'drama' || view === 'recommendations'" class="asset-section">
        <div class="toolbar">
          <div>
            <p class="eyebrow">{{ view === 'recommendations' ? t('recommendationsManagement') : t('contentAssets') }}</p>
            <h2>{{ view === 'recommendations' ? t('recommendationsManagement') : t('dramaLibrary') }}</h2>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="dramaFilters.keyword" :placeholder="t('keyword')" clearable @keyup.enter="loadDramas" @clear="loadDramas" />
            <el-select v-model="dramaFilters.contentType" clearable :placeholder="t('contentType')" @change="loadDramas" @clear="loadDramas">
              <el-option v-for="item in dramaFilterOptions('contentType')" :key="item.key" :label="filterOptionLabel(item)" :value="item.key" />
            </el-select>
            <el-select v-if="view !== 'recommendations'" v-model="dramaFilters.status" clearable :placeholder="t('statusAll')" @change="loadDramas" @clear="loadDramas">
              <el-option :label="t('online')" :value="1" />
              <el-option :label="t('offline')" :value="0" />
            </el-select>
            <el-select v-if="view === 'recommendations'" v-model="dramaFilters.status" clearable :placeholder="t('recommended')" @change="loadDramas" @clear="loadDramas">
              <el-option :label="t('yes')" :value="1" />
              <el-option :label="t('noText')" :value="0" />
            </el-select>
            <el-button @click="loadDramas">{{ t('search') }}</el-button>
            <el-button v-if="view !== 'recommendations'" type="primary" @click="editDrama(newDramaDraft())">{{ t('newDrama') }}</el-button>
          </div>
        </div>
        <el-table :data="pagedDramaList" border>
          <el-table-column prop="id" :label="t('id')" width="80" />
          <el-table-column :label="t('cover')" width="92">
            <template #default="{ row }">
              <img class="asset-cover" :src="field(row, 'verticalCoverUrl', 'vertical_cover_url', 'coverUrl', 'cover_url')" :alt="row.title" />
            </template>
          </el-table-column>
          <el-table-column prop="title" :label="t('dramaName')" min-width="180">
            <template #default="{ row }">
              <div class="asset-title">
                <strong>{{ row.title }}</strong>
                <span>{{ field(row, 'tags') || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('systemCategory')" min-width="190">
            <template #default="{ row }">
              <div class="asset-category-tags">
                <span>{{ optionText('background', field(row, 'background')) }}</span>
                <span>{{ optionText('theme', field(row, 'theme')) }}</span>
                <span>{{ optionText('setting', field(row, 'settingKey', 'setting_key')) }}</span>
                <span>{{ optionText('audience', field(row, 'audience')) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="t('episodes')" width="100">
            <template #default="{ row }">{{ formatNumber(field(row, 'episodeCount', 'episode_count', 'totalEpisodes', 'total_episodes')) }}</template>
          </el-table-column>
          <el-table-column :label="t('playCount')" width="110">
            <template #default="{ row }">{{ formatNumber(field(row, 'playCount', 'play_count')) }}</template>
          </el-table-column>
          <el-table-column :label="t('revenue')" width="110">
            <template #default="{ row }">{{ formatNumber(field(row, 'revenuePoints', 'revenue_points')) }}</template>
          </el-table-column>
          <el-table-column :label="t('status')" width="120">
            <template #default="{ row }">
              <el-tag :type="Number(field(row, 'status')) === 1 ? 'success' : 'info'">{{ statusLabel(field(row, 'status')) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('recommended')" width="110">
            <template #default="{ row }">
              <el-tag :type="Number(field(row, 'recommended')) === 1 ? 'warning' : 'info'">{{ Number(field(row, 'recommended')) === 1 ? t('yes') : t('noText') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('actions')" width="340">
            <template #default="{ row }">
              <el-button size="small" @click="editDrama(row)">{{ t('edit') }}</el-button>
              <el-button size="small" @click="openEpisodes(row)">{{ t('episodeConfig') }}</el-button>
              <el-button size="small" :type="Number(field(row, 'status')) === 1 ? 'warning' : 'success'" @click="toggleDramaStatus(row)">
                {{ Number(field(row, 'status')) === 1 ? t('takeOffline') : t('putOnline') }}
              </el-button>
              <el-button size="small" @click="toggleRecommend(row)">{{ Number(field(row, 'recommended')) === 1 ? t('unrecommend') : t('recommend') }}</el-button>
              <el-button size="small" type="danger" @click="removeDrama(row)">{{ t('delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="dramaPager.page"
            v-model:page-size="dramaPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="dramas.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>

      <section v-if="view === 'episode'" class="asset-section episode-ops-page">
        <div class="episode-page-layout">
          <aside class="episode-drama-sidebar">
            <div class="sidebar-header">
              <h3>{{ t('dramaLibrary') }}</h3>
              <el-input v-model="episodeDramaSearch" :placeholder="t('search')" clearable size="small" />
            </div>
            <div class="sidebar-drama-list">
              <div
                v-for="item in filteredEpisodeDramas"
                :key="item.id"
                class="sidebar-drama-item"
                :class="{ active: item.id === episodeDramaId }"
                @click="selectEpisodeDrama(item.id)"
              >
                <img :src="field(item, 'verticalCoverUrl', 'vertical_cover_url') || field(item, 'coverUrl', 'cover_url')" :alt="item.title" />
                <div class="sidebar-drama-info">
                  <strong>{{ item.title }}</strong>
                  <span>{{ field(item, 'totalEpisodes', 'total_episodes') || 0 }} {{ t('episodes') }}</span>
                </div>
              </div>
              <div v-if="!filteredEpisodeDramas.length" class="sidebar-empty">{{ t('noData') }}</div>
            </div>
          </aside>

          <div class="episode-main-content">
            <div class="toolbar">
              <div>
                <p class="eyebrow">{{ selectedDrama?.title || t('contentAssets') }}</p>
                <h2>{{ t('episodeManagement') }}</h2>
              </div>
              <div class="toolbar-actions">
                <el-input v-model="episodeFilters.keyword" :placeholder="t('episodeKeyword')" clearable @keyup.enter="loadEpisodes" @clear="loadEpisodes" />
                <el-select v-model="episodeFilters.accessType" clearable :placeholder="t('accessType')" @change="loadEpisodes" @clear="loadEpisodes">
                  <el-option :label="t('freePreview')" value="FREE" />
                  <el-option :label="t('paidEpisode')" value="POINTS" />
                </el-select>
                <el-select v-model="episodeFilters.status" clearable :placeholder="t('statusAll')" @change="loadEpisodes" @clear="loadEpisodes">
                  <el-option :label="t('online')" :value="1" />
                  <el-option :label="t('offline')" :value="0" />
                </el-select>
                <el-button @click="loadEpisodes">{{ t('search') }}</el-button>
                <el-button :disabled="!selectedDrama" @click="applyFreePreview">{{ t('applyFreePreview') }}</el-button>
                <el-button type="primary" :disabled="!episodeDramaId" @click="editEpisode(newEpisodeDraft())">{{ t('newEpisode') }}</el-button>
                <el-button type="success" :disabled="!episodeDramaId" @click="openBatchUpload()">{{ t('batchUpload') }}</el-button>
              </div>
            </div>

            <div class="episode-kpi-grid">
              <div class="episode-kpi-card">
                <span>{{ formatNumber(episodeStats.totalEpisodes) }}</span>
                <label>{{ t('totalEpisodes') }}</label>
              </div>
              <div class="episode-kpi-card accent">
                <span>{{ formatNumber(episodeStats.totalPlays) }}</span>
                <label>{{ t('totalPlayCount') }}</label>
              </div>
              <div class="episode-kpi-card">
                <span>{{ formatNumber(episodeStats.totalUnlockUsers) }}</span>
                <label>{{ t('totalUnlockUsers') }}</label>
              </div>
              <div class="episode-kpi-card">
                <span>{{ formatNumber(episodeStats.totalRevenue) }}</span>
                <label>{{ t('totalRevenuePoints') }}</label>
              </div>
              <div class="episode-kpi-card">
                <span>{{ formatNumber(field(selectedDrama, 'freeEpisodeCount', 'free_episode_count') || 0) }}</span>
                <label>{{ t('freeCount') }}</label>
              </div>
              <div class="episode-kpi-card">
                <span>{{ formatNumber(field(selectedDrama, 'episodePricePoints', 'episode_price_points') || 0) }}</span>
                <label>{{ t('episodePrice') }}</label>
              </div>
            </div>

            <div class="episode-long-list">
              <div v-if="pendingEpisodes.length" class="episode-list-body">
                <article v-for="pe in pendingEpisodes" :key="pe._id" class="episode-list-row episode-pending-row">
                  <div class="episode-index-block">
                    <span>{{ t('episodeNo') }}</span>
                    <strong>{{ pe.episodeNo }}</strong>
                  </div>
                  <div class="episode-pending-info">
                    <div class="pending-info-block">
                      <strong>{{ pe.title }}</strong>
                      <span v-if="pe.dramaName" class="pending-drama-name">{{ pe.dramaName }}</span>
                      <div class="pending-file-info">
                        <span v-if="pe.fileName" class="pending-file-name" :title="pe.fileName">{{ pe.fileName }}</span>
                        <span v-if="pe.fileSize" class="pending-file-size">{{ formatFileSize(pe.fileSize) }}</span>
                        <span v-if="pe.durationSeconds">{{ t('duration') }} {{ pe.durationSeconds }}s</span>
                      </div>
                    </div>
                  </div>
                  <div class="episode-pending-progress">
                    <el-progress
                      :percentage="pe.progress || 0"
                      :stroke-width="10"
                      :show-text="true"
                      :status="pe._status === 'fail' ? 'exception' : (pe._status === 'success' ? 'success' : '')"
                    />
                    <div class="pending-progress-actions">
                      <span class="upload-speed" v-if="pe._status === 'uploading'">{{ formatSpeed(pe.speed) }}</span>
                      <span v-if="pe._status === 'creating'" class="pending-creating-text">正在保存到数据库...</span>
                      <span v-if="pe._status === 'fail'" class="pending-fail-text">{{ pe.errorReason || '上传失败' }}</span>
                      <div class="pending-action-buttons">
                        <el-button v-if="pe._status === 'uploading'" size="small" text type="danger" @click="pe._controller?.abort(); pe._status = 'fail'; pe.errorReason = '已取消'">取消</el-button>
                        <el-button v-if="pe._status === 'fail'" size="small" type="warning" plain @click="retryPendingEpisode(pe)">重新上传</el-button>
                        <el-button v-if="pe._status === 'fail'" size="small" text type="danger" @click="removePendingEpisode(pe)">移除</el-button>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
              <div v-if="episodes.length" class="episode-list-body">
                <article v-for="row in pagedEpisodeList" :key="row.id" class="episode-list-row">
                  <div class="episode-index-block">
                    <span>{{ t('episodeNo') }}</span>
                    <strong>{{ field(row, 'episodeNo', 'episode_no') }}</strong>
                  </div>
                  <div class="episode-info-cell episode-main-cell">
                    <img :src="field(row, 'coverUrl', 'cover_url') || field(selectedDrama, 'verticalCoverUrl', 'vertical_cover_url', 'coverUrl', 'cover_url')" :alt="row.title" />
                    <div>
                      <strong>{{ field(row, 'title') }}</strong>
                      <span>{{ t('duration') }} {{ formatDuration(field(row, 'durationSeconds', 'duration_seconds')) }}</span>
                    </div>
                  </div>
                  <div class="episode-policy-cell">
                    <span class="biz-tag" :class="accessTagClass(row)">{{ accessTypeLabel(row) }}</span>
                    <strong>{{ formatNumber(field(row, 'pricePoints', 'price_points')) }} {{ t('credits') }}</strong>
                  </div>
                  <div class="episode-metrics-grid">
                    <div><label>{{ t('playUsers') }}</label><strong>{{ formatNumber(field(row, 'playUsers', 'play_users')) }}</strong></div>
                    <div><label>{{ t('playCount') }}</label><strong>{{ formatNumber(field(row, 'playCount', 'play_count')) }}</strong></div>
                    <div><label>{{ t('avgWatch') }}</label><strong>{{ formatDuration(field(row, 'avgWatchSeconds', 'avg_watch_seconds')) }}</strong></div>
                    <div><label>{{ t('completionRate') }}</label><strong class="metric-good">{{ formatPercent(field(row, 'completionRate', 'completion_rate')) }}</strong></div>
                    <div><label>{{ t('churnRate') }}</label><strong class="metric-warn">{{ formatPercent(field(row, 'churnRate', 'churn_rate')) }}</strong></div>
                    <div><label>{{ t('unlockUsers') }}</label><strong>{{ formatNumber(field(row, 'unlockUsers', 'unlock_users')) }}</strong></div>
                    <div><label>{{ t('revenue') }}</label><strong>{{ formatNumber(field(row, 'revenuePoints', 'revenue_points')) }}</strong></div>
                  </div>
                  <div class="episode-row-side">
                    <span class="biz-tag" :class="Number(field(row, 'status')) === 1 ? 'tag-online' : 'tag-offline'">{{ statusLabel(field(row, 'status')) }}</span>
                    <span v-if="transcodeTagMap[row.id]" class="biz-tag" :class="transcodeTagMap[row.id].cls">{{ transcodeTagMap[row.id].text }}</span>
                    <div class="row-actions episode-actions">
                      <el-button size="small" @click="openVideoPreview(row)">预览</el-button>
                      <el-button size="small" @click="editEpisode(row)">{{ t('edit') }}</el-button>
                      <el-button size="small" @click="adjustEpisodePrice(row)">{{ t('adjustPrice') }}</el-button>
                      <el-button size="small" @click="openEpisodeAnalysis(row)">{{ t('viewAnalysis') }}</el-button>
                      <el-button v-if="transcodeTagMap[row.id]?.status === -1" size="small" type="warning" @click="retryTranscode(row)">重转码</el-button>
                      <el-button size="small" :type="Number(field(row, 'status')) === 1 ? 'warning' : 'success'" @click="toggleEpisodeStatus(row)">{{ Number(field(row, 'status')) === 1 ? t('takeOffline') : t('putOnline') }}</el-button>
                      <el-button size="small" type="danger" @click="removeEpisode(row)">{{ t('delete') }}</el-button>
                    </div>
                  </div>
                </article>
              </div>
              <div v-else-if="!pendingEpisodes.length" class="ops-empty">
                <strong>{{ t('noEpisodeData') }}</strong>
                <span>{{ t('noEpisodeDataHint') }}</span>
              </div>
            </div>

            <div class="pagination-wrap" v-if="episodes.length">
              <el-pagination
                v-model:current-page="episodePager.page"
                v-model:page-size="episodePager.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="episodes.length"
                layout="total, sizes, prev, pager, next, jumper"
                background
              />
            </div>
          </div>
        </div>
      </section>

      <section v-if="view === 'dashboard'" class="analytics-section" v-loading="analyticsLoading">
        <div class="analytics-hero">
          <div>
            <p class="eyebrow">{{ t('dataOverview') }}</p>
            <h2>{{ t('dashboard') }}</h2>
            <p>{{ t('analyticsSubtitle') }}</p>
          </div>
          <div class="toolbar-actions analytics-filters">
            <el-select v-model="analyticsDays" :placeholder="t('lastDays')" @change="loadDashboard">
              <el-option :label="t('days7')" :value="7" />
              <el-option :label="t('days30')" :value="30" />
              <el-option :label="t('days90')" :value="90" />
              <el-option :label="t('days180')" :value="180" />
            </el-select>
            <el-button type="primary" @click="loadDashboard">{{ t('refresh') }}</el-button>
          </div>
        </div>

        <div class="metric-grid">
          <div class="metric-card">
            <span>{{ formatNumber(dashboardData.kpis?.totalUsers) }}</span>
            <label>{{ t('totalUsers') }}</label>
          </div>
          <div class="metric-card accent">
            <span>{{ formatNumber(dashboardData.kpis?.activeViewers) }}</span>
            <label>{{ t('activeViewers') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(dashboardData.kpis?.playEvents) }}</span>
            <label>{{ t('playEvents') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(dashboardData.kpis?.totalDramas) }}</span>
            <label>{{ t('totalTitles') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(dashboardData.kpis?.totalEpisodes) }}</span>
            <label>{{ t('totalEpisodes') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(dashboardData.kpis?.paidOrders) }}</span>
            <label>{{ t('paidOrders') }}</label>
          </div>
          <div class="metric-card wide">
            <span>{{ formatMoney(dashboardData.kpis?.paidAmountCents, dashboardData.kpis?.currency || 'USD') }}</span>
            <label>{{ t('paidAmount') }}</label>
          </div>
        </div>

        <div class="chart-panel">
          <div class="panel-title">
            <h3>{{ t('playTrend') }}</h3>
            <span>{{ t('lastDays') }} · {{ analyticsDays }}</span>
          </div>
          <div v-if="dashboardData.playTrend?.length" class="trend-bars">
            <div v-for="row in dashboardData.playTrend" :key="field(row, 'statDate', 'stat_date')" class="trend-item">
              <div class="trend-track">
                <span class="trend-bar" :style="{ height: trendHeight(row) }"></span>
              </div>
              <strong>{{ formatNumber(field(row, 'playEvents', 'play_events')) }}</strong>
              <small>{{ shortDate(field(row, 'statDate', 'stat_date')) }}</small>
            </div>
          </div>
          <el-empty v-else :description="t('noData')" />
        </div>

        <div class="analytics-columns">
          <div class="analytics-panel">
            <div class="panel-title"><h3>{{ t('dramaRanking') }}</h3></div>
            <el-table :data="pagedDashDramas" border>
              <el-table-column type="index" width="52" />
              <el-table-column :label="t('dramaTitle')" min-width="180">
                <template #default="{ row }">{{ field(row, 'dramaTitle', 'title', 'drama_title') }}</template>
              </el-table-column>
              <el-table-column :label="t('playEvents')" width="110">
                <template #default="{ row }">{{ formatNumber(field(row, 'playEvents', 'play_events')) }}</template>
              </el-table-column>
              <el-table-column :label="t('viewers')" width="110">
                <template #default="{ row }">{{ formatNumber(field(row, 'viewers')) }}</template>
              </el-table-column>
              <el-table-column :label="t('unlockCount')" width="110">
                <template #default="{ row }">{{ formatNumber(field(row, 'unlockCount', 'unlock_count')) }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrap">
              <el-pagination
                v-model:current-page="dashDramaPager.page"
                v-model:page-size="dashDramaPager.pageSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="(dashboardData.dramaRanking || []).length"
                layout="total, sizes, prev, pager, next, jumper"
                background
                size="small"
              />
            </div>
          </div>

          <div class="analytics-panel">
            <div class="panel-title"><h3>{{ t('recentOrders') }}</h3></div>
            <el-table :data="pagedDashOrders" border>
              <el-table-column :label="t('orderNo')" min-width="200">
                <template #default="{ row }">{{ field(row, 'orderNo', 'order_no') }}</template>
              </el-table-column>
              <el-table-column :label="t('user')" width="120">
                <template #default="{ row }">{{ field(row, 'username', 'user_id') }}</template>
              </el-table-column>
              <el-table-column :label="t('points')" width="100">
                <template #default="{ row }">{{ formatNumber(field(row, 'points')) }}</template>
              </el-table-column>
              <el-table-column :label="t('amount')" width="120">
                <template #default="{ row }">{{ formatMoney(field(row, 'amountCents', 'amount_cents'), field(row, 'currency') || 'USD') }}</template>
              </el-table-column>
              <el-table-column :label="t('status')" width="100">
                <template #default="{ row }">
                  <el-tag :type="orderStatusTagType(field(row, 'status'))">{{ field(row, 'status') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('time')" width="150">
                <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
              </el-table-column>
            </el-table>
            <div class="pagination-wrap">
              <el-pagination
                v-model:current-page="dashOrderPager.page"
                v-model:page-size="dashOrderPager.pageSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="(dashboardData.recentOrders || []).length"
                layout="total, sizes, prev, pager, next, jumper"
                background
                size="small"
              />
            </div>
          </div>
        </div>
      </section>

      <section v-if="view === 'analytics'" class="analytics-section" v-loading="analyticsLoading">
        <div class="analytics-hero">
          <div>
            <p class="eyebrow">{{ t('analytics') }}</p>
            <h2>{{ t('analyticsDashboard') }}</h2>
            <p>{{ t('analyticsSubtitle') }}</p>
          </div>
          <div class="toolbar-actions analytics-filters">
            <el-select v-model="analyticsDays" :placeholder="t('lastDays')" @change="loadAnalytics">
              <el-option :label="t('days7')" :value="7" />
              <el-option :label="t('days30')" :value="30" />
              <el-option :label="t('days90')" :value="90" />
              <el-option :label="t('days180')" :value="180" />
            </el-select>
            <el-select v-model="analyticsDramaId" clearable filterable :placeholder="t('allTitles')" @change="loadAnalytics">
              <el-option v-for="item in dramas" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
            <el-button type="primary" @click="loadAnalytics">{{ t('refresh') }}</el-button>
          </div>
        </div>

        <div class="metric-grid">
          <div class="metric-card">
            <span>{{ formatNumber(stat('totalUsers')) }}</span>
            <label>{{ t('totalUsers') }}</label>
          </div>
          <div class="metric-card accent">
            <span>{{ formatNumber(stat('activeViewers')) }}</span>
            <label>{{ t('activeViewers') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(stat('playEvents')) }}</span>
            <label>{{ t('playEvents') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(stat('watchedDramas')) }}</span>
            <label>{{ t('watchedDramas') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(stat('unlockPoints')) }}</span>
            <label>{{ t('unlockPoints') }}</label>
          </div>
          <div class="metric-card">
            <span>{{ formatNumber(stat('paidOrders')) }}</span>
            <label>{{ t('paidOrders') }}</label>
          </div>
          <div class="metric-card wide">
            <span>{{ formatMoney(stat('paidAmountCents'), stat('currency') || 'USD') }}</span>
            <label>{{ t('paidAmount') }}</label>
          </div>
        </div>

        <div class="analytics-charts-grid">
          <div class="chart-panel large">
            <div class="panel-title">
              <h3>{{ t('playTrend') }}</h3>
              <span>{{ t('lastDays') }} · {{ analyticsDays }}</span>
            </div>
            <div ref="trendChartRef" class="chart-container"></div>
          </div>
          <div class="chart-panel">
            <div class="panel-title"><h3>{{ t('dramaRanking') }}</h3></div>
            <div ref="dramaChartRef" class="chart-container"></div>
          </div>
          <div class="chart-panel">
            <div class="panel-title"><h3>{{ t('viewerActivity') }}</h3></div>
            <div ref="viewerActivityChartRef" class="chart-container"></div>
          </div>
          <div class="chart-panel">
            <div class="panel-title"><h3>{{ t('userSegmentation') }}</h3></div>
            <div ref="userSegmentChartRef" class="chart-container"></div>
          </div>
          <div class="chart-panel wide-panel">
            <div class="panel-title"><h3>{{ t('episodeCompletionAnalysis') }}</h3></div>
            <div ref="episodeCompletionChartRef" class="chart-container"></div>
          </div>
        </div>

        <div class="analytics-columns">
          <div class="analytics-panel">
            <div class="panel-title">
              <h3>{{ t('dramaRanking') }} ({{ t('detail') }})</h3>
              <el-pagination
                v-model:current-page="analyticsPager.dramasPage"
                v-model:page-size="analyticsPager.dramasSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="analytics.dramas.length"
                background
                small
                layout="total, sizes, prev, pager, next"
              />
            </div>
            <el-table :data="pagedDramas" border stripe height="300">
              <el-table-column type="index" width="52" />
              <el-table-column :label="t('dramaTitle')" min-width="180">
                <template #default="{ row }">{{ field(row, 'dramaTitle', 'title', 'drama_title') }}</template>
              </el-table-column>
              <el-table-column :label="t('playEvents')" width="110" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'playEvents', 'play_events')) }}</template>
              </el-table-column>
              <el-table-column :label="t('viewers')" width="110" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'viewers')) }}</template>
              </el-table-column>
              <el-table-column :label="t('unlockCount')" width="110" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'unlockCount', 'unlock_count')) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="analytics-panel">
            <div class="panel-title">
              <h3>{{ t('viewerRanking') }}</h3>
              <el-pagination
                v-model:current-page="analyticsPager.viewersPage"
                v-model:page-size="analyticsPager.viewersSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="analytics.viewers.length"
                background
                small
                layout="total, sizes, prev, pager, next"
              />
            </div>
            <el-table :data="pagedViewers" border stripe height="300">
              <el-table-column type="index" width="52" />
              <el-table-column :label="t('name')" min-width="140">
                <template #default="{ row }">{{ field(row, 'nickname', 'phone', 'userId', 'user_id') || '-' }}</template>
              </el-table-column>
              <el-table-column :label="t('watchedDramas')" width="130" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'watchedDramas', 'watched_dramas')) }}</template>
              </el-table-column>
              <el-table-column :label="t('progress')" width="120" sortable>
                <template #default="{ row }">{{ formatDuration(field(row, 'progressSeconds', 'progress_seconds')) }}</template>
              </el-table-column>
              <el-table-column :label="t('lastWatch')" width="150">
                <template #default="{ row }">{{ formatDateTime(field(row, 'lastWatchAt', 'last_watch_at')) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div class="analytics-columns">
          <div class="analytics-panel">
            <div class="panel-title">
              <h3>{{ t('episodeStats') }}</h3>
              <el-pagination
                v-model:current-page="analyticsPager.episodesPage"
                v-model:page-size="analyticsPager.episodesSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="analytics.episodes.length"
                background
                small
                layout="total, sizes, prev, pager, next"
              />
            </div>
            <el-table :data="pagedEpisodes" border stripe height="350">
              <el-table-column :label="t('no')" width="80" sortable>
                <template #default="{ row }">{{ field(row, 'episodeNo', 'episode_no') }}</template>
              </el-table-column>
              <el-table-column :label="t('title')" min-width="180">
                <template #default="{ row }">{{ field(row, 'episodeTitle', 'title', 'episode_title') || '-' }}</template>
              </el-table-column>
              <el-table-column :label="t('playEvents')" width="110" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'playEvents', 'play_events')) }}</template>
              </el-table-column>
              <el-table-column :label="t('viewers')" width="110" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'viewers')) }}</template>
              </el-table-column>
              <el-table-column :label="t('avgProgress')" width="130">
                <template #default="{ row }">{{ formatDuration(field(row, 'avgProgressSeconds', 'avg_progress_seconds')) }}</template>
              </el-table-column>
              <el-table-column :label="t('unlockUsers')" width="120" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'unlockUsers', 'unlock_users')) }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="analytics-panel compact-panel">
            <div class="panel-title">
              <h3>{{ t('episodeReach') }}</h3>
              <el-pagination
                v-model:current-page="analyticsPager.reachPage"
                v-model:page-size="analyticsPager.reachSize"
                :page-sizes="[5, 10, 20, 50]"
                :total="analytics.reach.length"
                background
                small
                layout="total, sizes, prev, pager, next"
              />
            </div>
            <el-table :data="pagedReach" border stripe height="350">
              <el-table-column :label="t('no')" width="80" sortable>
                <template #default="{ row }">{{ field(row, 'episodeNo', 'episode_no') }}</template>
              </el-table-column>
              <el-table-column :label="t('title')" min-width="160">
                <template #default="{ row }">{{ field(row, 'episodeTitle', 'title', 'episode_title') || '-' }}</template>
              </el-table-column>
              <el-table-column :label="t('reachedUsers')" width="130" sortable>
                <template #default="{ row }">{{ formatNumber(field(row, 'reachedUsers', 'reached_users')) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </section>

      <section v-if="view === 'users'">
        <div class="toolbar">
          <div>
            <p class="eyebrow">{{ t('userOperations') }}</p>
            <h2>{{ t('userPool') }}</h2>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="userFilters.keyword" :placeholder="t('keyword')" clearable @keyup.enter="loadUsers" @clear="loadUsers" />
            <el-select v-model="userFilters.status" clearable :placeholder="t('statusAll')" @change="loadUsers" @clear="loadUsers">
              <el-option :label="t('enabled')" :value="1" />
              <el-option :label="t('disabled')" :value="0" />
            </el-select>
            <el-button type="primary" @click="loadUsers">{{ t('search') }}</el-button>
            <el-button type="success" @click="openUserDialog()">{{ t('newUser') }}</el-button>
          </div>
        </div>
        <el-table :data="pagedUserList" border>
          <el-table-column prop="id" :label="t('id')" width="80" />
          <el-table-column :label="t('username')" min-width="130">
            <template #default="{ row }">{{ field(row, 'username') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('phone')" min-width="140">
            <template #default="{ row }">{{ field(row, 'phone') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('nickname')" min-width="140">
            <template #default="{ row }">{{ field(row, 'nickname') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('points')" width="110">
            <template #default="{ row }">{{ formatNumber(field(row, 'points')) }}</template>
          </el-table-column>
          <el-table-column :label="t('status')" width="120">
            <template #default="{ row }">
              <el-tag :type="Number(field(row, 'status')) === 1 ? 'success' : 'info'">{{ userStatusLabel(field(row, 'status')) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('createdAt')" width="160">
            <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
          </el-table-column>
          <el-table-column :label="t('actions')" width="330">
            <template #default="{ row }">
              <el-button size="small" @click="openPoints(row)">{{ t('grantPoints') }}</el-button>
              <el-button size="small" type="primary" @click="openUserDialog(row)">{{ t('edit') }}</el-button>
              <el-button v-if="Number(field(row, 'status')) !== 1" size="small" type="success" @click="changeUserStatus(row, 1)">{{ t('enabled') }}</el-button>
              <el-button v-else size="small" type="warning" @click="changeUserStatus(row, 0)">{{ t('disabled') }}</el-button>
              <el-button size="small" type="danger" @click="confirmDeleteUser(row)">{{ t('delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="userPager.page"
            v-model:page-size="userPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="users.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>

      <section v-if="view === 'feedback'">
        <div class="toolbar">
          <div>
            <p class="eyebrow">{{ t('userOperations') }}</p>
            <h2>{{ t('feedbackManagement') }}</h2>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="feedbackFilters.keyword" :placeholder="t('keyword')" clearable @keyup.enter="loadFeedbacks" @clear="loadFeedbacks" />
            <el-select v-model="feedbackFilters.type" clearable :placeholder="t('feedbackType')" @change="loadFeedbacks" @clear="loadFeedbacks">
              <el-option :label="t('feedbackBug')" value="BUG" />
              <el-option :label="t('feedbackSuggestion')" value="SUGGESTION" />
              <el-option :label="t('feedbackConsultation')" value="CONSULTATION" />
              <el-option :label="t('feedbackComplaint')" value="COMPLAINT" />
              <el-option :label="t('feedbackOther')" value="OTHER" />
            </el-select>
            <el-select v-model="feedbackFilters.status" clearable :placeholder="t('statusAll')" @change="loadFeedbacks" @clear="loadFeedbacks">
              <el-option :label="t('pending')" value="PENDING" />
              <el-option :label="t('processing')" value="PROCESSING" />
              <el-option :label="t('processed')" value="PROCESSED" />
              <el-option :label="t('closed')" value="CLOSED" />
            </el-select>
            <el-button type="primary" @click="loadFeedbacks">{{ t('search') }}</el-button>
          </div>
        </div>
        <el-table :data="pagedFeedList" border>
          <el-table-column prop="id" :label="t('id')" width="80" />
          <el-table-column :label="t('user')" min-width="130">
            <template #default="{ row }">{{ field(row, 'username', 'nickname', 'user_id') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('feedbackType')" width="120">
            <template #default="{ row }">
              <el-tag :type="feedbackTypeTagType(field(row, 'type'))">{{ feedbackTypeLabel(field(row, 'type')) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('feedbackTitle')" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ field(row, 'title') || field(row, 'feedback_title') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('feedbackStatus')" width="120">
            <template #default="{ row }">
              <el-tag :type="feedbackStatusTagType(field(row, 'status'))">{{ feedbackStatusLabel(field(row, 'status')) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('createdAt')" width="160">
            <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
          </el-table-column>
          <el-table-column :label="t('actions')" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="openFeedbackDetail(row)">{{ t('detail') }}</el-button>
              <el-button size="small" type="primary" @click="openFeedbackReply(row)">{{ t('replyFeedback') }}</el-button>
              <el-button size="small" @click="toggleFeedbackStatus(row)">{{ t('feedbackStatus') }}</el-button>
              <el-button size="small" type="danger" @click="confirmDeleteFeedback(row)">{{ t('delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="feedPager.page"
            v-model:page-size="feedPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="feedbacks.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>

      <section v-if="view === 'orders'">
        <div class="toolbar">
          <div>
            <p class="eyebrow">{{ t('tradeCenter') }}</p>
            <h2>{{ t('orderManagement') }}</h2>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="orderFilters.keyword" :placeholder="t('keyword')" clearable @keyup.enter="loadOrders" @clear="loadOrders" />
            <el-select v-model="orderFilters.status" clearable :placeholder="t('statusAll')" @change="loadOrders" @clear="loadOrders">
              <el-option :label="t('pending')" value="PENDING" />
              <el-option :label="t('paid')" value="PAID" />
              <el-option :label="t('refunded')" value="REFUNDED" />
              <el-option :label="t('cancelled')" value="CANCELLED" />
              <el-option :label="t('closed')" value="CLOSED" />
            </el-select>
            <el-button type="primary" @click="loadOrders">{{ t('search') }}</el-button>
          </div>
        </div>
        <el-table :data="pagedOrderList" border>
          <el-table-column :label="t('orderNo')" min-width="210" show-overflow-tooltip>
            <template #default="{ row }">{{ field(row, 'orderNo', 'order_no') }}</template>
          </el-table-column>
          <el-table-column :label="t('nickname')" min-width="130">
            <template #default="{ row }">{{ field(row, 'nickname', 'phone') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('product')" min-width="150">
            <template #default="{ row }">{{ field(row, 'productName', 'product_name') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('points')" width="100">
            <template #default="{ row }">{{ formatNumber(field(row, 'points')) }}</template>
          </el-table-column>
          <el-table-column :label="t('amount')" width="120">
            <template #default="{ row }">{{ formatMoney(field(row, 'amountCents', 'amount_cents'), field(row, 'currency') || 'USD') }}</template>
          </el-table-column>
          <el-table-column :label="t('status')" width="120">
            <template #default="{ row }"><el-tag :type="orderStatusTagType(field(row, 'status'))">{{ orderStatusLabel(field(row, 'status')) }}</el-tag></template>
          </el-table-column>
          <el-table-column :label="t('createdAt')" width="160">
            <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
          </el-table-column>
          <el-table-column :label="t('actions')" width="260">
            <template #default="{ row }">
              <el-button v-if="field(row, 'status') === 'PENDING'" size="small" type="success" @click="markPaid(row)">{{ t('markPaid') }}</el-button>
              <el-button v-if="field(row, 'status') === 'PAID'" size="small" type="warning" @click="refund(row)">{{ t('refund') }}</el-button>
              <el-button v-if="field(row, 'status') === 'PENDING'" size="small" @click="setOrderStatus(row, 'CANCELLED')">{{ t('cancel') }}</el-button>
              <el-button v-if="field(row, 'status') === 'PENDING'" size="small" @click="setOrderStatus(row, 'CLOSED')">{{ t('close') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="adminOrderPager.page"
            v-model:page-size="adminOrderPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="orders.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>

      <section v-if="view === 'roles'">
        <div class="toolbar">
          <h2>{{ t('rolePermission') }}</h2>
          <el-button type="primary" @click="editRole({ status: 1, permissions: '' })">{{ t('newRole') }}</el-button>
        </div>
        <div class="split-grid">
          <div>
            <div class="panel-title"><h3>{{ t('roles') }}</h3></div>
            <el-table :data="pagedRoleList" border>
              <el-table-column prop="id" :label="t('id')" width="70" />
              <el-table-column :label="t('roleName')" min-width="130">
                <template #default="{ row }">{{ field(row, 'name') }}</template>
              </el-table-column>
              <el-table-column :label="t('roleCode')" min-width="130">
                <template #default="{ row }">{{ field(row, 'code') }}</template>
              </el-table-column>
              <el-table-column :label="t('permissions')" min-width="260" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag
                    v-for="code in (field(row, 'permissions') || '').split(',').filter(Boolean)"
                    :key="code"
                    :type="code === '*' ? 'danger' : getPermTagType(code)"
                    size="small"
                    class="perm-tag"
                  >{{ getPermLabel(code) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column :label="t('actions')" width="170">
                <template #default="{ row }">
                  <el-button size="small" @click="editRole(row)">{{ t('edit') }}</el-button>
                  <el-button size="small" type="danger" @click="removeRole(row)">{{ t('delete') }}</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrap">
              <el-pagination
                v-model:current-page="rolePager.page"
                v-model:page-size="rolePager.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="roles.length"
                layout="total, sizes, prev, pager, next, jumper"
                background
              />
            </div>
          </div>
          <div>
            <div class="panel-title">
              <h3>{{ t('adminUsers') }}</h3>
              <el-button type="primary" size="small" @click="openCreateAdmin">{{ t('newAdmin') }}</el-button>
            </div>
            <el-table :data="pagedAdminList" border>
              <el-table-column :label="t('username')" min-width="120">
                <template #default="{ row }">{{ field(row, 'username') }}</template>
              </el-table-column>
              <el-table-column :label="t('nickname')" min-width="120">
                <template #default="{ row }">{{ field(row, 'nickname') || '-' }}</template>
              </el-table-column>
              <el-table-column :label="t('assignRoles')" min-width="220">
                <template #default="{ row }">
                  <el-select v-model="row.roleIds" multiple collapse-tags collapse-tags-tooltip @change="assignRoles(row)">
                    <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="t('actions')" width="170">
                <template #default="{ row }">
                  <el-button size="small" @click="editAdmin(row)">{{ t('edit') }}</el-button>
                  <el-button size="small" type="danger" @click="confirmDeleteAdmin(row)">{{ t('delete') }}</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrap">
              <el-pagination
                v-model:current-page="adminUserPager.page"
                v-model:page-size="adminUserPager.pageSize"
                :page-sizes="[10, 20, 50, 100]"
                :total="admins.length"
                layout="total, sizes, prev, pager, next, jumper"
                background
              />
            </div>
          </div>
        </div>
      </section>

      <section v-if="view === 'logs'">
        <div class="toolbar">
          <h2>{{ t('operationLogs') }}</h2>
          <el-button type="primary" @click="loadLogs">{{ t('refresh') }}</el-button>
        </div>
        <el-table :data="pagedLogList" border>
          <el-table-column prop="id" :label="t('id')" width="80" />
          <el-table-column :label="t('username')" width="130">
            <template #default="{ row }">{{ field(row, 'adminUsername', 'admin_username') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('method')" width="100">
            <template #default="{ row }">{{ field(row, 'method') }}</template>
          </el-table-column>
          <el-table-column :label="t('path')" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">{{ field(row, 'path') }}</template>
          </el-table-column>
          <el-table-column :label="t('statusCode')" width="120">
            <template #default="{ row }">{{ field(row, 'statusCode', 'status_code') }}</template>
          </el-table-column>
          <el-table-column :label="t('ip')" width="150">
            <template #default="{ row }">{{ field(row, 'ip') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('time')" width="170">
            <template #default="{ row }">{{ formatDateTime(field(row, 'createdAt', 'created_at')) }}</template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="logPager.page"
            v-model:page-size="logPager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="logs.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>

      <section v-if="view === 'announcement'">
        <div class="toolbar">
          <div>
            <p class="eyebrow">{{ t('systemSettings') }}</p>
            <h2>{{ t('announcementManagement') }}</h2>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="announcementFilters.keyword" :placeholder="t('keyword')" clearable @keyup.enter="loadAnnouncements" @clear="loadAnnouncements" />
            <el-select v-model="announcementFilters.status" clearable :placeholder="t('statusAll')" @change="loadAnnouncements" @clear="loadAnnouncements">
              <el-option :label="t('draft')" value="DRAFT" />
              <el-option :label="t('published')" value="PUBLISHED" />
              <el-option :label="t('expired')" value="EXPIRED" />
            </el-select>
            <el-select v-model="announcementFilters.type" clearable :placeholder="t('announcementType')" @change="loadAnnouncements" @clear="loadAnnouncements">
              <el-option :label="t('systemAnnouncement')" value="SYSTEM" />
              <el-option :label="t('activityAnnouncement')" value="ACTIVITY" />
            </el-select>
            <el-button type="primary" @click="editAnnouncement(newAnnouncementDraft())">{{ t('newAnnouncement') }}</el-button>
          </div>
        </div>
        <el-table :data="pagedAnnounceList" border>
          <el-table-column prop="id" :label="t('id')" width="80" />
          <el-table-column :label="t('title')" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ field(row, 'title') || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('announcementType')" width="140">
            <template #default="{ row }">{{ announcementTypeLabel(field(row, 'type')) }}</template>
          </el-table-column>
          <el-table-column :label="t('isTop')" width="100">
            <template #default="{ row }">
              <el-tag v-if="Number(field(row, 'isTop', 'is_top')) === 1" type="warning">{{ t('yes') }}</el-tag>
              <span v-else>{{ t('noText') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('status')" width="120">
            <template #default="{ row }">
              <el-tag :type="announcementStatusTagType(field(row, 'status'))">{{ announcementStatusLabel(field(row, 'status')) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('publishTime')" width="160">
            <template #default="{ row }">{{ formatDateTime(field(row, 'publishTime', 'publish_time')) }}</template>
          </el-table-column>
          <el-table-column :label="t('readCount')" width="100">
            <template #default="{ row }">{{ formatNumber(field(row, 'readCount', 'read_count')) }}</template>
          </el-table-column>
          <el-table-column :label="t('actions')" width="340">
            <template #default="{ row }">
              <el-button size="small" @click="editAnnouncement(row)">{{ t('edit') }}</el-button>
              <el-button v-if="field(row, 'status') !== 'PUBLISHED'" size="small" type="success" @click="publishAnnouncementAction(row)">{{ t('publish') }}</el-button>
              <el-button v-else size="small" type="warning" @click="unpublishAnnouncementAction(row)">{{ t('unpublish') }}</el-button>
              <el-button size="small" @click="toggleAnnouncementTopAction(row)">{{ Number(field(row, 'isTop', 'is_top')) === 1 ? t('cancelTop') : t('isTop') }}</el-button>
              <el-button size="small" type="danger" @click="confirmDeleteAnnouncement(row)">{{ t('delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="announcePager.page"
            v-model:page-size="announcePager.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="announcements.length"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>
      </template>
    </el-main>
  </el-container>

  <el-dialog v-model="categoryDialog" :title="t('categoryConfig')" width="640px">
    <el-form :model="categoryForm" label-width="110px">
      <div class="form-grid two">
        <el-form-item :label="t('categoryGroup')" required>
          <el-select v-model="categoryForm.groupKey" @change="selectCategoryGroup">
            <el-option v-for="group in categoryGroupOptions" :key="group.key" :label="filterGroupLabel(group.key)" :value="group.key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('categoryOptionName')" required><el-input v-model.trim="categoryForm.optionLabelKey" /></el-form-item>
        <el-form-item :label="t('sort')"><el-input-number v-model="categoryForm.optionSortOrder" :precision="0" :step="1" /></el-form-item>
      </div>
      <el-form-item :label="t('status')"><el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
    </el-form>
    <template #footer><el-button type="primary" @click="saveCategory">{{ t('save') }}</el-button></template>
  </el-dialog>

  <el-dialog v-model="dramaDialog" :title="t('dramaAsset')" width="860px">
    <el-form :model="dramaForm" label-width="110px">
      <div class="form-grid two">
        <el-form-item :label="t('title')" required><el-input v-model.trim="dramaForm.title" /></el-form-item>
        <el-form-item :label="t('contentType')">
          <el-select v-model="dramaForm.contentType" :placeholder="t('contentType')">
            <el-option v-for="item in dramaFilterOptions('contentType')" :key="item.key" :label="filterOptionLabel(item)" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('tags')"><el-input v-model="dramaForm.tags" :placeholder="t('tagsPlaceholder')" /></el-form-item>
      </div>
      <el-form-item :label="t('synopsis')"><el-input v-model="dramaForm.description" type="textarea" :rows="3" /></el-form-item>
      <div class="form-grid four">
        <el-form-item :label="filterGroupLabel('background')">
          <el-select v-model="dramaForm.background" :placeholder="filterGroupLabel('background')">
            <el-option v-for="item in dramaFilterOptions('background')" :key="item.key" :label="filterOptionLabel(item)" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="filterGroupLabel('theme')">
          <el-select v-model="dramaForm.theme" :placeholder="filterGroupLabel('theme')">
            <el-option v-for="item in dramaFilterOptions('theme')" :key="item.key" :label="filterOptionLabel(item)" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="filterGroupLabel('setting')">
          <el-select v-model="dramaForm.setting" :placeholder="filterGroupLabel('setting')">
            <el-option v-for="item in dramaFilterOptions('setting')" :key="item.key" :label="filterOptionLabel(item)" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="filterGroupLabel('audience')">
          <el-select v-model="dramaForm.audience" :placeholder="filterGroupLabel('audience')">
            <el-option v-for="item in dramaFilterOptions('audience')" :key="item.key" :label="filterOptionLabel(item)" :value="item.key" />
          </el-select>
        </el-form-item>
      </div>
      <div class="form-grid two">
        <el-form-item :label="t('coverUrl')">
          <div class="upload-row">
            <el-input v-model="dramaForm.coverUrl" />
            <el-upload :show-file-list="false" accept="image/*" :http-request="options => uploadLocal(options, dramaForm, 'coverUrl', 'image', 'drama-coverUrl')">
              <el-button :loading="getUploadProgress('drama-coverUrl').uploading">
              {{ getUploadProgress('drama-coverUrl').uploading ? `${getUploadProgress('drama-coverUrl').percent}%` : t('localUpload') }}
            </el-button>
            </el-upload>
            <el-button v-if="getUploadProgress('drama-coverUrl').uploading" size="small" text type="danger" @click="cancelUpload('drama-coverUrl')">取消</el-button>
            <span v-if="getUploadProgress('drama-coverUrl').uploading" class="upload-speed">{{ formatSpeed(getUploadProgress('drama-coverUrl').speed) }}</span>
          </div>
          <el-progress v-if="getUploadProgress('drama-coverUrl').uploading" :percentage="getUploadProgress('drama-coverUrl').percent" :stroke-width="3" :show-text="false" style="margin-top: 4px" />
        </el-form-item>
        <el-form-item :label="t('verticalCover')">
          <div class="upload-row">
            <el-input v-model="dramaForm.verticalCoverUrl" />
            <el-upload :show-file-list="false" accept="image/*" :http-request="options => uploadLocal(options, dramaForm, 'verticalCoverUrl', 'image', 'drama-verticalCoverUrl')">
              <el-button :loading="getUploadProgress('drama-verticalCoverUrl').uploading">
                {{ getUploadProgress('drama-verticalCoverUrl').uploading ? `${getUploadProgress('drama-verticalCoverUrl').percent}%` : t('localUpload') }}
              </el-button>
            </el-upload>
            <el-button v-if="getUploadProgress('drama-verticalCoverUrl').uploading" size="small" text type="danger" @click="cancelUpload('drama-verticalCoverUrl')">取消</el-button>
            <span v-if="getUploadProgress('drama-verticalCoverUrl').uploading" class="upload-speed">{{ formatSpeed(getUploadProgress('drama-verticalCoverUrl').speed) }}</span>
          </div>
          <el-progress v-if="getUploadProgress('drama-verticalCoverUrl').uploading" :percentage="getUploadProgress('drama-verticalCoverUrl').percent" :stroke-width="3" :show-text="false" style="margin-top: 4px" />
        </el-form-item>
        <el-form-item :label="t('horizontalCover')">
          <div class="upload-row">
            <el-input v-model="dramaForm.horizontalCoverUrl" />
            <el-upload :show-file-list="false" accept="image/*" :http-request="options => uploadLocal(options, dramaForm, 'horizontalCoverUrl', 'image', 'drama-horizontalCoverUrl')">
              <el-button :loading="getUploadProgress('drama-horizontalCoverUrl').uploading">
                {{ getUploadProgress('drama-horizontalCoverUrl').uploading ? `${getUploadProgress('drama-horizontalCoverUrl').percent}%` : t('localUpload') }}
              </el-button>
            </el-upload>
            <el-button v-if="getUploadProgress('drama-horizontalCoverUrl').uploading" size="small" text type="danger" @click="cancelUpload('drama-horizontalCoverUrl')">取消</el-button>
            <span v-if="getUploadProgress('drama-horizontalCoverUrl').uploading" class="upload-speed">{{ formatSpeed(getUploadProgress('drama-horizontalCoverUrl').speed) }}</span>
          </div>
          <el-progress v-if="getUploadProgress('drama-horizontalCoverUrl').uploading" :percentage="getUploadProgress('drama-horizontalCoverUrl').percent" :stroke-width="3" :show-text="false" style="margin-top: 4px" />
        </el-form-item>
        <el-form-item :label="t('onlineTime')">
          <el-date-picker v-model="dramaForm.onlineTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </div>
      <div class="form-grid two numeric-grid">
        <el-form-item :label="t('freeCount')"><el-input-number v-model="dramaForm.freeEpisodeCount" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('episodePrice')"><el-input-number v-model="dramaForm.episodePricePoints" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('wholePrice')"><el-input-number v-model="dramaForm.wholePricePoints" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('total')"><el-input-number v-model="dramaForm.totalEpisodes" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('sort')"><el-input-number v-model="dramaForm.sortOrder" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('recommended')"><el-switch v-model="dramaForm.recommended" /></el-form-item>
        <el-form-item :label="t('status')"><el-switch v-model="dramaForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </div>
    </el-form>
    <template #footer><el-button type="primary" @click="saveDrama">{{ t('save') }}</el-button></template>
  </el-dialog>

  <el-dialog v-model="episodeDialog" :title="t('episode')" width="860px">
    <el-form :model="episodeForm" label-width="110px">
      <div class="form-grid two">
        <el-form-item :label="t('selectTitle')" required><el-select v-model="episodeForm.dramaId"><el-option v-for="item in dramas" :key="item.id" :label="item.title" :value="item.id" /></el-select></el-form-item>
        <el-form-item :label="t('episodeNo')" required><el-input-number v-model="episodeForm.episodeNo" :min="1" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('name')" required><el-input v-model.trim="episodeForm.title" /></el-form-item>
        <el-form-item :label="t('duration')"><el-input-number v-model="episodeForm.durationSeconds" :min="0" :precision="0" :step="1" /></el-form-item>
      </div>
      <el-form-item :label="t('synopsis')"><el-input v-model="episodeForm.description" type="textarea" /></el-form-item>
      <el-form-item :label="t('coverUrl')">
        <div class="upload-row">
          <el-input v-model="episodeForm.coverUrl" />
          <el-upload :show-file-list="false" accept="image/*" :http-request="options => uploadLocal(options, episodeForm, 'coverUrl', 'image', 'episode-coverUrl')">
            <el-button :loading="getUploadProgress('episode-coverUrl').uploading">
              {{ getUploadProgress('episode-coverUrl').uploading ? `${getUploadProgress('episode-coverUrl').percent}%` : t('localUpload') }}
            </el-button>
          </el-upload>
          <el-button v-if="getUploadProgress('episode-coverUrl').uploading" size="small" text type="danger" @click="cancelUpload('episode-coverUrl')">取消</el-button>
          <span v-if="getUploadProgress('episode-coverUrl').uploading" class="upload-speed">{{ formatSpeed(getUploadProgress('episode-coverUrl').speed) }}</span>
        </div>
        <el-progress v-if="getUploadProgress('episode-coverUrl').uploading" :percentage="getUploadProgress('episode-coverUrl').percent" :stroke-width="3" :show-text="false" style="margin-top: 4px" />
      </el-form-item>
      <el-form-item :label="t('videoUrl')" required>
        <div class="upload-row">
          <el-input
            v-model="episodeForm.videoUrl"
            :placeholder="episodeForm.storageProvider === 'cloudflare' ? t('cloudflareUidPlaceholder') : ''"
          />
          <el-upload :show-file-list="false" accept="video/*,.m3u8" :http-request="options => uploadLocal(options, episodeForm, 'videoUrl', 'video', 'episode-videoUrl')">
            <el-button type="primary" :loading="getUploadProgress('episode-videoUrl').uploading">
              {{ getUploadProgress('episode-videoUrl').uploading ? `${getUploadProgress('episode-videoUrl').percent}%` : (episodeForm.storageProvider === 'cloudflare' ? t('uploadToCloudflare') : t('localUpload')) }}
            </el-button>
          </el-upload>
          <el-button v-if="getUploadProgress('episode-videoUrl').uploading" size="small" text type="danger" @click="cancelUpload('episode-videoUrl')">取消</el-button>
          <span v-if="getUploadProgress('episode-videoUrl').uploading" class="upload-speed">{{ formatSpeed(getUploadProgress('episode-videoUrl').speed) }}</span>
        </div>
        <el-progress
          v-if="getUploadProgress('episode-videoUrl').uploading || getUploadProgress('episode-videoUrl').error"
          :percentage="getUploadProgress('episode-videoUrl').percent"
          :stroke-width="10"
          :show-text="true"
          :status="getUploadProgress('episode-videoUrl').error ? 'exception' : 'success'"
          style="margin-top: 6px"
        />
        <div v-if="getUploadProgress('episode-videoUrl').error" style="color: #f56c6c; font-size: 12px; margin-top: 4px">
          {{ getUploadProgress('episode-videoUrl').error }}
        </div>
      </el-form-item>
      <div class="form-grid two">
        <el-form-item :label="t('storage')"><el-segmented v-model="episodeForm.storageProvider" :options="['local', 'oss', 'cos', 'cloudflare']" /></el-form-item>
        <el-form-item :label="t('accessType')"><el-segmented v-model="episodeForm.accessType" :options="accessTypeOptions" /></el-form-item>
        <el-form-item :label="t('credits')"><el-input-number v-model="episodeForm.pricePoints" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('sort')"><el-input-number v-model="episodeForm.sortOrder" :min="0" :precision="0" :step="1" /></el-form-item>
        <el-form-item :label="t('status')"><el-switch v-model="episodeForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </div>
    </el-form>
    <template #footer><el-button type="primary" @click="saveEpisode">{{ t('save') }}</el-button></template>
  </el-dialog>

  <el-dialog v-model="batchDialog" :title="t('batchUploadEpisodes')" width="780px">
    <div class="batch-upload-section">
      <div class="batch-upload-info">
        <span>{{ t('dramaName') }}：<strong>{{ selectedDrama?.title || '-' }}</strong></span>
        <span>{{ t('startEpisodeNo') }}：<el-input-number v-model="batchForm.startEpisodeNo" :min="1" :precision="0" :step="1" size="small" style="width: 120px" :disabled="vpsTask.running" /></span>
      </div>

      <el-tabs v-model="batchMode" class="batch-tabs">
        <el-tab-pane :label="t('localUpload')" name="local">
          <el-upload
            ref="batchUploadRef"
            :show-file-list="true"
            :auto-upload="false"
            multiple
            accept="video/*,.m3u8"
            :on-change="handleBatchFileChange"
            :on-remove="handleBatchFileRemove"
            :file-list="batchFileList"
            drag
          >
            <el-icon size="40" color="#d4af68"><UploadFilled /></el-icon>
            <div style="margin-top: 8px; color: #d4af68; font-weight: 600">{{ t('clickOrDrag') }}</div>
            <div style="color: #999; font-size: 12px; margin-top: 4px">{{ t('batchUploadHint') }}</div>
          </el-upload>
          <div v-if="batchForm.episodes.length" class="batch-episode-list">
            <div class="batch-list-header">
              <span>{{ t('selectedEpisodes') }} ({{ batchForm.episodes.length }})</span>
              <div>
                <el-button v-if="batchForm.episodes.some(e => e.uploadStatus === 'fail')" size="small" type="warning" plain @click="retryAllFailed" :loading="batchUploading">
                  {{ t('retryAllFailed') }}
                </el-button>
                <el-button size="small" text @click="clearBatchList">{{ t('clearAll') }}</el-button>
              </div>
            </div>
            <div class="batch-episode-items">
              <div
                v-for="(ep, idx) in batchForm.episodes"
                :key="idx"
                class="batch-episode-row"
                style="flex-wrap: wrap; align-items: center"
              >
                <span class="batch-episode-no">{{ ep.episodeNo }}</span>
                <el-input v-model="ep.title" :placeholder="t('episodeTitle')" size="small" style="flex: 1" />
                <el-input-number v-model="ep.pricePoints" :min="0" :precision="0" :step="1" size="small" style="width: 100px" />
                <el-select v-model="ep.accessType" size="small" style="width: 100px">
                  <el-option :label="t('paidEpisode')" value="POINTS" />
                  <el-option :label="t('freePreview')" value="FREE" />
                </el-select>
                <el-button size="small" text type="danger" @click="removeBatchEpisode(idx)">{{ t('delete') }}</el-button>
                <el-tag
                  v-if="ep.uploadStatus && ep.uploadStatus !== 'pending'"
                  size="small"
                  :type="batchEpisodeTagType(ep.uploadStatus)"
                  style="width: 70px; text-align: center"
                >
                  {{ batchEpisodeStatusLabel(ep.uploadStatus) }}
                </el-tag>
                <el-progress
                  v-if="ep.uploadStatus === 'uploading' || ep.uploadStatus === 'success' || ep.uploadStatus === 'fail'"
                  :percentage="ep.progress || 0"
                  :stroke-width="8"
                  :show-text="true"
                  :status="ep.uploadStatus === 'fail' ? 'exception' : (ep.uploadStatus === 'success' ? 'success' : '')"
                  style="flex-basis: 100%; margin-top: 4px"
                />
                <div v-if="ep.uploadStatus === 'uploading'" style="flex-basis: 100%; display: flex; align-items: center; gap: 8px; margin-top: 2px">
                  <span class="upload-speed">{{ formatSpeed(ep.speed) }}</span>
                </div>
                <div v-if="ep.uploadStatus === 'uploading'" style="flex-basis: 100%; margin-top: 4px">
                  <el-button size="small" text type="danger" @click="ep._controller?.abort(); ep.uploadStatus = 'fail'; ep.errorReason = '已取消'">取消上传</el-button>
                </div>
                <div v-if="ep.errorReason" style="flex-basis: 100%; color: #f56c6c; font-size: 12px; margin-top: 2px">
                  {{ ep.errorReason }}
                </div>
                <div v-if="ep.uploadStatus === 'fail'" style="flex-basis: 100%; margin-top: 4px">
                  <el-button size="small" type="warning" plain @click="retryBatchEpisode(idx)" :loading="ep.uploadStatus === 'uploading'">
                    {{ t('retryUpload') }}
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="t('vpsTransferMode')" name="vps">
          <el-alert :title="t('vpsTransferHint')" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
          <div style="margin-bottom: 12px">
            <el-button type="primary" plain :loading="vpsScanning" :disabled="vpsTask.running" @click="scanVpsFiles">
              {{ t('scanFiles') }}
            </el-button>
            <span style="margin-left: 12px; color: #999; font-size: 12px">{{ t('scanHint') }}</span>
          </div>

          <div v-if="!vpsFiles.length && !vpsScanning" style="color: #999; padding: 24px; text-align: center">
            {{ t('noFilesScanned') }}
          </div>

          <div v-if="vpsFiles.length" class="batch-episode-list">
            <div class="batch-list-header">
              <span>{{ t('selectedEpisodes') }} ({{ vpsFiles.length }})</span>
              <span v-if="vpsTask.running || vpsTask.status === 'done' || vpsTask.status === 'error'" style="margin-left: 16px; font-size: 12px">
                {{ t('processed') }}: {{ vpsTask.processed }}/{{ vpsTask.total }}
                <el-tag size="small" :type="vpsTaskTagType" style="margin-left: 8px">{{ vpsTaskStatusLabel }}</el-tag>
              </span>
            </div>
            <el-progress
              v-if="vpsTask.running || vpsTask.status === 'done' || vpsTask.status === 'error'"
              :percentage="vpsTaskPercentage"
              :status="vpsTaskProgressStatus"
              style="margin: 8px 0"
            />
            <div class="batch-episode-items">
              <div v-for="f in vpsFiles" :key="f.path" class="batch-episode-row">
                <span class="batch-episode-no">{{ f.sortOrder }}</span>
                <span style="flex: 1; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ f.fileName }}</span>
                <span style="width: 90px; text-align: right; color: #999; font-size: 12px">{{ formatBytes(f.sizeBytes) }}</span>
                <el-tag size="small" :type="vpsFileTagType(f.status)" style="width: 70px; text-align: center">
                  {{ t('fileStatus.' + f.status) || f.status }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <template #footer>
      <el-button @click="batchDialog = false">{{ t('cancel') }}</el-button>
      <el-button v-if="batchMode === 'local'" type="primary" :disabled="!batchForm.episodes.length || batchUploading" @click="submitBatchUpload">
        {{ batchUploading ? t('uploading') : t('confirmUpload') }}
      </el-button>
      <el-button v-else type="primary" :disabled="!vpsFiles.length || vpsTask.running" @click="startVpsUpload">
        {{ vpsTask.running ? t('batchUploadRunning') : t('startBatchUpload') }}
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="pointsDialog" :title="t('grantPoints')" width="460px">
    <el-form :model="pointsForm" label-width="90px">
      <el-form-item :label="t('nickname')">
        <el-input :model-value="field(selectedUser, 'nickname', 'username', 'phone') || '-'" disabled />
      </el-form-item>
      <el-form-item :label="t('delta')" required>
        <el-input-number v-model="pointsForm.delta" :precision="0" :step="1" />
      </el-form-item>
      <el-form-item :label="t('remark')">
        <el-input v-model="pointsForm.remark" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pointsDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="savePoints">{{ t('confirm') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="userDialog" :title="userForm.id ? t('editUser') : t('newUser')" width="560px">
    <el-form :model="userForm" label-width="90px" ref="userFormRef" :rules="userFormRules">
      <el-form-item :label="t('username')" required prop="username">
        <el-input v-model.trim="userForm.username" :disabled="!!userForm.id" />
      </el-form-item>
      <el-form-item v-if="!userForm.id" :label="t('password')">
        <el-input v-model="userForm.password" type="password" :placeholder="t('passwordPlaceholder')" show-password />
      </el-form-item>
      <el-form-item :label="t('phone')">
        <el-input v-model.trim="userForm.phone" />
      </el-form-item>
      <el-form-item :label="t('nickname')">
        <el-input v-model.trim="userForm.nickname" />
      </el-form-item>
      <el-form-item :label="t('avatarUrl')">
        <div class="upload-row">
          <el-input v-model.trim="userForm.avatarUrl" />
          <el-upload :show-file-list="false" accept="image/*" :http-request="options => uploadUserAvatar(options)">
            <el-button :loading="uploadingUserAvatar">{{ t('localUpload') }}</el-button>
          </el-upload>
        </div>
      </el-form-item>
      <el-form-item v-if="!userForm.id" :label="t('points')">
        <el-input-number v-model="userForm.points" :min="0" :precision="0" />
      </el-form-item>
      <el-form-item v-else :label="t('status')">
        <el-switch v-model="userForm.statusSwitch" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="userDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="saveUser">{{ t('save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="roleDialog" :title="roleForm.id ? t('editRole') : t('newRole')" width="620px">
    <el-form ref="roleFormRef" :model="roleForm" label-width="110px">
      <el-form-item :label="t('roleName')" required>
        <el-input v-model.trim="roleForm.name" @input="onRoleNameInput" />
      </el-form-item>
      <el-form-item :label="t('roleCode')" required>
        <div class="role-code-field">
          <el-input v-model.trim="roleForm.code" placeholder="ROLE_CODE" />
          <el-button
            v-if="roleCodeSuggestion && roleForm.code !== roleCodeSuggestion"
            size="small"
            type="primary"
            text
            @click="applyCodeSuggestion"
          >{{ t('useSuggestion') }}: {{ roleCodeSuggestion }}</el-button>
          <span class="field-hint">{{ t('roleCodeHint') }}</span>
        </div>
      </el-form-item>
      <el-form-item :label="t('permissions')" required>
        <div class="permission-tree-wrapper">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTreeData"
            show-checkbox
            node-key="id"
            :default-checked-keys="checkedPermissionKeys"
            :props="{ label: 'label', children: 'children' }"
            :expand-on-click-node="false"
            @check="onPermissionCheck"
          />
          <div class="permission-count-row">
            <span>{{ t('selectedPermissions') }}: {{ checkedPermissionKeys.length }}</span>
            <el-button size="small" text @click="checkAllPermissions">{{ t('selectAll') }}</el-button>
            <el-button size="small" text @click="clearAllPermissions">{{ t('clearAll') }}</el-button>
          </div>
        </div>
      </el-form-item>
      <el-form-item :label="t('status')"><el-switch v-model="roleForm.status" :active-value="1" :inactive-value="0" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="roleDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="saveRole">{{ t('save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="announcementDialog" :title="announcementForm.id ? t('editAnnouncement') : t('newAnnouncement')" width="640px">
    <el-form :model="announcementForm" label-width="110px">
      <el-form-item :label="t('title')" required>
        <el-input v-model.trim="announcementForm.title" />
      </el-form-item>
      <el-form-item :label="t('content')" required>
        <el-input v-model="announcementForm.content" type="textarea" :rows="5" />
      </el-form-item>
      <div class="form-grid two">
        <el-form-item :label="t('announcementType')" required>
          <el-select v-model="announcementForm.type" :placeholder="t('announcementType')">
            <el-option :label="t('systemAnnouncement')" value="SYSTEM" />
            <el-option :label="t('activityAnnouncement')" value="ACTIVITY" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('isTop')">
          <el-switch v-model="announcementForm.isTop" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </div>
      <div class="form-grid two">
        <el-form-item :label="t('effectivePeriod')">
          <el-date-picker v-model="announcementForm.effectiveStart" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="t('startTime')" />
        </el-form-item>
        <el-form-item :label="t('endTime')">
          <el-date-picker v-model="announcementForm.effectiveEnd" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="t('endTime')" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="announcementDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="saveAnnouncement">{{ t('save') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="feedbackDetailDialog" :title="t('feedbackDetail')" width="600px">
    <div v-if="feedbackDetailData" class="feedback-detail">
      <div class="feedback-detail-header">
        <h3>{{ field(feedbackDetailData, 'title') || '-' }}</h3>
        <el-tag :type="feedbackStatusTagType(field(feedbackDetailData, 'status'))">{{ feedbackStatusLabel(field(feedbackDetailData, 'status')) }}</el-tag>
      </div>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item :label="t('feedbackType')">{{ feedbackTypeLabel(field(feedbackDetailData, 'type')) }}</el-descriptions-item>
        <el-descriptions-item :label="t('user')">{{ field(feedbackDetailData, 'username', 'nickname') || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('createdAt')">{{ formatDateTime(field(feedbackDetailData, 'createdAt', 'created_at')) }}</el-descriptions-item>
      </el-descriptions>
      <el-form-item :label="t('feedbackContent')" label-width="90px">
        <div style="white-space: pre-wrap; padding: 8px 12px; background: #f5f7fa; border-radius: 4px; width: 100%;">{{ field(feedbackDetailData, 'content') || '-' }}</div>
      </el-form-item>
      <el-form-item v-if="field(feedbackDetailData, 'adminReply')" :label="t('replyContent')" label-width="90px">
        <div style="white-space: pre-wrap; padding: 8px 12px; background: #ecf5ff; border-radius: 4px; width: 100%;">{{ field(feedbackDetailData, 'adminReply') }}</div>
      </el-form-item>
    </div>
    <template #footer>
      <el-button @click="feedbackDetailDialog = false">{{ t('close') }}</el-button>
      <el-button type="primary" @click="openFeedbackReply(feedbackDetailData)">{{ t('replyFeedback') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="feedbackReplyDialog" :title="t('replyFeedback')" width="520px">
    <el-form :model="feedbackReplyForm" label-width="90px">
      <el-form-item :label="t('replyContent')" required>
        <el-input v-model="feedbackReplyForm.reply" type="textarea" :rows="4" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="feedbackReplyDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="submitFeedbackReply">{{ t('confirm') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="createAdminDialog" :title="t('newAdmin')" width="480px">
    <el-form :model="createAdminForm" label-width="90px">
      <el-form-item :label="t('username')" required>
        <el-input v-model.trim="createAdminForm.username" :placeholder="t('username')" />
        <div v-if="createAdminForm.username && !validateUsername(createAdminForm.username)" class="form-tip">
          {{ t('usernameRule') }}
        </div>
      </el-form-item>
      <el-form-item :label="t('password')" required>
        <el-input v-model="createAdminForm.password" type="password" show-password :placeholder="t('password')" />
        <div v-if="createAdminForm.password && !validatePassword(createAdminForm.password)" class="form-tip">
          {{ t('passwordRule') }}
        </div>
      </el-form-item>
      <el-form-item :label="t('nickname')">
        <el-input v-model.trim="createAdminForm.nickname" :placeholder="t('nickname')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createAdminDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="submitCreateAdmin">{{ t('confirm') }}</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editAdminDialog" :title="t('edit')" width="480px">
    <el-form :model="editAdminForm" label-width="90px">
      <el-form-item :label="t('username')">
        <el-input :model-value="editAdminForm.username" disabled />
      </el-form-item>
      <el-form-item :label="t('nickname')">
        <el-input v-model.trim="editAdminForm.nickname" :placeholder="t('nickname')" />
      </el-form-item>
      <el-form-item :label="t('password')">
        <el-input v-model="editAdminForm.password" type="password" show-password :placeholder="t('passwordLeaveEmpty')" />
      </el-form-item>
      <div v-if="editAdminForm.password && !validatePassword(editAdminForm.password)" class="form-tip">
        {{ t('passwordRule') }}
      </div>
    </el-form>
    <template #footer>
      <el-button @click="editAdminDialog = false">{{ t('cancel') }}</el-button>
      <el-button type="primary" @click="submitEditAdmin">{{ t('save') }}</el-button>
    </template>
  </el-dialog>

  <!-- 视频预览弹窗 (HLS优先,回退到原始MP4) -->
  <el-dialog v-model="videoPreview.visible" title="视频预览" width="800px" @close="closeVideoPreview" destroy-on-close>
    <div v-if="videoPreview.loading" style="text-align: center; padding: 40px 0;">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p style="margin-top: 12px;">加载视频中...</p>
    </div>
    <div v-else-if="videoPreview.error" style="text-align: center; padding: 40px 0;">
      <p style="color: #f56c6c;">{{ videoPreview.error }}</p>
      <el-button v-if="videoPreview.canRetry" type="warning" size="small" @click="retryTranscode(videoPreview.row)" style="margin-top: 12px;">重新转码</el-button>
    </div>
    <video
      v-else
      ref="videoPreviewRef"
      :key="videoPreview.src"
      controls
      autoplay
      style="width: 100%; max-height: 480px; background: #000;"
      :src="videoPreview.isHls ? undefined : videoPreview.src"
    />
    <div v-if="!videoPreview.loading && !videoPreview.error" style="margin-top: 12px; font-size: 12px; color: #909399;">
      <span>{{ videoPreview.isHls ? 'HLS 流播放 (m3u8)' : '原始视频播放 (mp4)' }}</span>
      <span v-if="videoPreview.transcodeStatus === 1" style="margin-left: 12px; color: #e6a23c;">转码中...</span>
      <span v-else-if="videoPreview.transcodeStatus === 0" style="margin-left: 12px; color: #909399;">未转码</span>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, User, SwitchButton, Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import Hls from 'hls.js'
import { api } from './api.js'
import { messages, normalizeLocale, adminI18n } from './i18n.js'
import { applyLocaleSideEffects, streamI18n, loadStreamLocaleMessages } from './locales/streamI18n.js'
import { languageOptions as streamLanguageOptions } from '../../shared/i18n/locale-options.js'
import {
  field as fieldUtil,
  formatNumber as formatNumberUtil,
  formatMoney as formatMoneyUtil,
  formatDuration as formatDurationUtil,
  formatPercent as formatPercentUtil,
  formatDateTime as formatDateTimeUtil,
  validatePassword as validatePasswordUtil,
  validateUsername as validateUsernameUtil
} from './utils/helpers.js'
import UserApp from './UserApp.vue'
import CurrencyRatePage from './pages/CurrencyRatePage.vue'
import AdminProfilePage from './pages/AdminProfilePage.vue'
import AdminPointProductPage from './pages/AdminPointProductPage.vue'
import AdminMembershipPage from './pages/AdminMembershipPage.vue'
import AdminShopPage from './pages/AdminShopPage.vue'
import AdminPointRecordPage from './pages/AdminPointRecordPage.vue'
import AdminAutoRenewalPage from './pages/AdminAutoRenewalPage.vue'
import AdminMembershipStats from './pages/AdminMembershipStats.vue'
import AdminCommentPage from './pages/AdminCommentPage.vue'

for (const key of ['adminToken', 'adminRefreshToken', 'userToken', 'userRefreshToken', 'user']) {
  localStorage.removeItem(key)
}
const token = ref('')
const userToken = ref('')
const authReady = ref(false)
const view = ref('dashboard')
const adminPermissions = ref([])
const openedMenus = ['contentManagement', 'userOperations', 'tradeCenter', 'dataCenter', 'systemSettings']
const adminT = adminI18n.global.t
const streamT = streamI18n.global.t
const streamLocale = streamI18n.global.locale
const router = useRouter()
const route = useRoute()
const authMode = ref('login')
const loginRole = ref('admin')
const loginForm = reactive({ account: '', password: '', nickname: '', captchaId: '', captchaCode: '' })
const authLoading = ref(false)
const authError = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)
const publicStats = ref({ dramaCount: 0, episodeCount: 0 })
const categoryFilterGroups = ref([])
const dramas = ref([])
const episodes = ref([])
const episodeDramaId = ref(null)
const episodeDramaSearch = ref('')
const users = ref([])
const orders = ref([])
const admins = ref([])
const roles = ref([])
const logs = ref([])
const announcements = ref([])
const feedbacks = ref([])
const analyticsDays = ref(30)
const analyticsDramaId = ref(null)
const analyticsLoading = ref(false)
const dramaFilters = reactive({ keyword: '', contentType: '', status: null })
const episodeFilters = reactive({ keyword: '', accessType: '', status: null })
const userFilters = reactive({ keyword: '', status: null })

function getStarStyle(n) {
  const seed = n * 137.508
  return {
    left: `${(seed * 7.3) % 100}%`,
    top: `${(seed * 11.7) % 100}%`,
    animationDelay: `${(seed % 5) * 0.6}s`,
    animationDuration: `${3 + (seed % 4)}s`,
    opacity: 0.3 + (seed % 3) * 0.2
  }
}
const orderFilters = reactive({ keyword: '', status: '' })
const announcementFilters = reactive({ keyword: '', status: '', type: '' })
const feedbackFilters = reactive({ keyword: '', type: '', status: '' })
const dashboardData = ref({ kpis: {}, dramaRanking: [], playTrend: [], recentOrders: [] })
const analytics = reactive({
  overview: {},
  dramas: [],
  episodes: [],
  trend: [],
  viewers: [],
  reach: []
})

const trendChartRef = ref(null)
const dramaChartRef = ref(null)
const viewerActivityChartRef = ref(null)
const userSegmentChartRef = ref(null)
const episodeCompletionChartRef = ref(null)
let trendChartInstance = null
let dramaChartInstance = null
let viewerActivityChartInstance = null
let userSegmentChartInstance = null
let episodeCompletionChartInstance = null

const analyticsPager = reactive({
  dramasPage: 1,
  dramasSize: 10,
  viewersPage: 1,
  viewersSize: 10,
  episodesPage: 1,
  episodesSize: 10,
  reachPage: 1,
  reachSize: 10
})

const dramaPager = reactive({ page: 1, pageSize: 10 })
const episodePager = reactive({ page: 1, pageSize: 10 })
const userPager = reactive({ page: 1, pageSize: 10 })
const adminOrderPager = reactive({ page: 1, pageSize: 10 })
const adminUserPager = reactive({ page: 1, pageSize: 10 })
const rolePager = reactive({ page: 1, pageSize: 10 })
const logPager = reactive({ page: 1, pageSize: 10 })
const announcePager = reactive({ page: 1, pageSize: 10 })
const feedPager = reactive({ page: 1, pageSize: 10 })
const dashDramaPager = reactive({ page: 1, pageSize: 10 })
const dashOrderPager = reactive({ page: 1, pageSize: 10 })

const categoryDialog = ref(false)
const dramaDialog = ref(false)
const episodeDialog = ref(false)
const batchDialog = ref(false)
const batchUploadRef = ref(null)
const batchUploading = ref(false)
const batchFileList = ref([])
const uploadingUserAvatar = ref(false)
const uploadProgress = reactive({})
const uploadControllers = reactive({})
const pendingEpisodes = reactive([])
function getUploadProgress(key) {
  return uploadProgress[key] || { uploading: false, percent: 0, error: null, speed: 0 }
}
function setUploadProgress(key, patch) {
  uploadProgress[key] = { ...getUploadProgress(key), ...patch }
}
const batchForm = reactive({
  startEpisodeNo: 1,
  episodes: [],
  files: []
})
const batchMode = ref('local')
const vpsScanning = ref(false)
const vpsFiles = ref([])
const vpsTask = reactive({
  running: false,
  status: 'none',
  total: 0,
  processed: 0,
  success: 0,
  fail: 0,
  elapsedMillis: 0,
  files: []
})
let vpsPollingTimer = null
const pointsDialog = ref(false)
const roleDialog = ref(false)
const createAdminDialog = ref(false)
const createAdminForm = reactive({ username: '', password: '', nickname: '' })
const editAdminDialog = ref(false)
const editAdminForm = reactive({ id: null, username: '', nickname: '', password: '' })
const categoryForm = reactive({})
// 表单初始字段仅作声明,实际值由 copyTo(newDramaDraft()/normalize(row)) 填充;
// coverObjectKey 等字段透传给后端,删短剧时用于级联删 R2 对象
const dramaForm = reactive({ coverObjectKey: '', horizontalCoverObjectKey: '', verticalCoverObjectKey: '' })
const episodeForm = reactive({ coverObjectKey: '' })
const videoPreviewRef = ref(null)
const videoPreview = reactive({
  visible: false,
  loading: false,
  error: null,
  canRetry: false,
  src: '',
  isHls: false,
  transcodeStatus: 0,
  row: null
})
let videoPreviewHls = null
const transcodeStatusMap = reactive({})
let transcodePollTimer = null
const pointsForm = reactive({ delta: 100, remark: '' })
const roleForm = reactive({})
const roleFormRef = ref(null)
const permissionTreeRef = ref(null)
const checkedPermissionKeys = ref([])
const lastAutoCode = ref('')
const announcementDialog = ref(false)
const feedbackDetailDialog = ref(false)
const feedbackReplyDialog = ref(false)
const announcementForm = reactive({})
const feedbackDetailData = ref(null)
const feedbackReplyForm = reactive({ id: null, reply: '' })

const permissionCatalog = [
  {
    group: 'dashboard',
    label: '数据驾驶舱',
    items: [
      { code: 'dashboard:view', label: '查看数据驾驶舱' }
    ]
  },
  {
    group: 'content',
    label: '内容管理',
    items: [
      { code: 'content:manage', label: '管理短剧/剧集/分类' }
    ]
  },
  {
    group: 'user',
    label: '用户与积分',
    items: [
      { code: 'user:manage', label: '管理用户' },
      { code: 'point:manage', label: '调整积分' }
    ]
  },
  {
    group: 'order',
    label: '订单管理',
    items: [
      { code: 'order:manage', label: '管理订单' }
    ]
  },
  {
    group: 'role',
    label: '角色权限',
    items: [
      { code: 'role:manage', label: '管理角色与权限' }
    ]
  },
  {
    group: 'analytics',
    label: '数据分析',
    items: [
      { code: 'analytics:view', label: '查看数据分析' }
    ]
  },
  {
    group: 'log',
    label: '操作日志',
    items: [
      { code: 'log:view', label: '查看操作日志' }
    ]
  },
  {
    group: 'wildcard',
    label: '全部权限',
    items: [
      { code: '*', label: '超级管理员（全部权限）' }
    ]
  }
]

const roleCodePresets = {
  '超级管理员': 'SUPER_ADMIN',
  '超级管理': 'SUPER_ADMIN',
  '管理员': 'ADMIN',
  '内容运营': 'CONTENT_OPERATOR',
  '内容': 'CONTENT_OPERATOR',
  '运营': 'OPERATOR',
  '用户运营': 'USER_OPERATOR',
  '用户管理': 'USER_MANAGER',
  '财务': 'FINANCE',
  '财务审计': 'FINANCE_AUDITOR',
  '审计': 'AUDITOR',
  '数据分析': 'DATA_ANALYST',
  '分析员': 'DATA_ANALYST',
  '只读': 'VIEWER',
  '观察员': 'VIEWER',
  '客服': 'CUSTOMER_SERVICE',
  '测试': 'TESTER'
}
const selectedUser = ref(null)
const userDialog = ref(false)
const userFormRef = ref(null)
const userForm = reactive({ id: null, username: '', phone: '', password: '', nickname: '', avatarUrl: '', points: 0, statusSwitch: 1 })
const userFormRules = {
  username: [{ required: true, message: '请填写用户名', trigger: 'blur' }]
}

const authModeOptions = computed(() => [
  { label: streamT('auth.signIn'), value: 'login' },
  { label: streamT('auth.createAccount'), value: 'register' }
])

const loginRoleOptions = computed(() => [
  { label: streamT('auth.adminLogin'), value: 'admin' },
  { label: streamT('auth.userLogin'), value: 'user' }
])

const accessTypeOptions = computed(() => [
  { label: t('freePreview'), value: 'FREE' },
  { label: t('paidEpisode'), value: 'POINTS' }
])

const selectedDrama = computed(() => dramas.value.find(item => item.id === episodeDramaId.value) || null)

const filteredEpisodeDramas = computed(() => {
  const keyword = episodeDramaSearch.value.trim().toLowerCase()
  if (!keyword) return dramas.value
  return dramas.value.filter(item => (item.title || '').toLowerCase().includes(keyword))
})

function selectEpisodeDrama(dramaId) {
  episodeDramaId.value = dramaId
  loadEpisodes()
}

const episodeStats = computed(() => ({
  totalEpisodes: episodes.value.length,
  totalPlays: sumRows(episodes.value, 'playCount', 'play_count'),
  totalUnlockUsers: sumRows(episodes.value, 'unlockUsers', 'unlock_users'),
  totalRevenue: sumRows(episodes.value, 'revenuePoints', 'revenue_points')
}))

const pagedDramas = computed(() => {
  const start = (analyticsPager.dramasPage - 1) * analyticsPager.dramasSize
  return analytics.dramas.slice(start, start + analyticsPager.dramasSize)
})

const pagedViewers = computed(() => {
  const start = (analyticsPager.viewersPage - 1) * analyticsPager.viewersSize
  return analytics.viewers.slice(start, start + analyticsPager.viewersSize)
})

const pagedEpisodes = computed(() => {
  const start = (analyticsPager.episodesPage - 1) * analyticsPager.episodesSize
  return analytics.episodes.slice(start, start + analyticsPager.episodesSize)
})

const pagedReach = computed(() => {
  const start = (analyticsPager.reachPage - 1) * analyticsPager.reachSize
  return analytics.reach.slice(start, start + analyticsPager.reachSize)
})

const pagedDramaList = computed(() => {
  const start = (dramaPager.page - 1) * dramaPager.pageSize
  return dramas.value.slice(start, start + dramaPager.pageSize)
})

const pagedEpisodeList = computed(() => {
  const start = (episodePager.page - 1) * episodePager.pageSize
  return episodes.value.slice(start, start + episodePager.pageSize)
})

const pagedUserList = computed(() => {
  const start = (userPager.page - 1) * userPager.pageSize
  return users.value.slice(start, start + userPager.pageSize)
})

const pagedOrderList = computed(() => {
  const start = (adminOrderPager.page - 1) * adminOrderPager.pageSize
  return orders.value.slice(start, start + adminOrderPager.pageSize)
})

const pagedAdminList = computed(() => {
  const start = (adminUserPager.page - 1) * adminUserPager.pageSize
  return admins.value.slice(start, start + adminUserPager.pageSize)
})

const pagedRoleList = computed(() => {
  const start = (rolePager.page - 1) * rolePager.pageSize
  return roles.value.slice(start, start + rolePager.pageSize)
})

const pagedLogList = computed(() => {
  const start = (logPager.page - 1) * logPager.pageSize
  return logs.value.slice(start, start + logPager.pageSize)
})

const pagedAnnounceList = computed(() => {
  const start = (announcePager.page - 1) * announcePager.pageSize
  return announcements.value.slice(start, start + announcePager.pageSize)
})

const pagedFeedList = computed(() => {
  const start = (feedPager.page - 1) * feedPager.pageSize
  return feedbacks.value.slice(start, start + feedPager.pageSize)
})

const pagedDashDramas = computed(() => {
  const start = (dashDramaPager.page - 1) * dashDramaPager.pageSize
  return (dashboardData.value.dramaRanking || []).slice(start, start + dashDramaPager.pageSize)
})

const pagedDashOrders = computed(() => {
  const start = (dashOrderPager.page - 1) * dashOrderPager.pageSize
  return (dashboardData.value.recentOrders || []).slice(start, start + dashOrderPager.pageSize)
})

const permissionTreeData = computed(() => {
  return permissionCatalog.map(group => ({
    id: group.group,
    label: group.label,
    children: group.items.map(item => ({
      id: item.code,
      label: item.label
    }))
  }))
})

const allPermissionCodes = computed(() => {
  const codes = []
  for (const group of permissionCatalog) {
    for (const item of group.items) {
      codes.push(item.code)
    }
  }
  return codes
})

const viewPermissionMap = {
  dashboard: 'dashboard:view',
  drama: 'content:manage',
  episode: 'content:manage',
  category: 'content:manage',
  comment: 'content:manage',
  recommendations: 'content:manage',
  users: 'user:manage',
  feedback: 'user:manage',
  orders: 'order:manage',
  package: 'order:manage',
  analytics: 'analytics:view',
  roles: 'role:manage',
  logs: 'log:view',
  announcement: 'system:manage',
  currencyRate: 'system:manage',
  membership: 'user:manage',
  shop: 'shop:manage',
  pointRecords: 'point:manage',
  autoRenewal: 'order:manage',
  membershipStats: 'user:manage'
}

const menuVisibility = computed(() => {
  const perms = adminPermissions.value
  const isSuper = perms.includes('*')
  const check = (view) => {
    if (isSuper) return true
    const required = viewPermissionMap[view]
    if (!required) return true
    return perms.includes(required)
  }
  return {
    dashboard: check('dashboard'),
    contentManagement: check('drama') || check('episode') || check('category') || check('comment'),
    drama: check('drama'),
    episode: check('episode'),
    category: check('category'),
    comment: check('comment'),
    recommendations: check('recommendations'),
    userOperations: check('users') || check('feedback') || check('membership'),
    users: check('users'),
    feedback: check('feedback'),
    membership: check('membership'),
    tradeCenter: check('orders') || check('package') || check('shop') || check('autoRenewal'),
    orders: check('orders'),
    package: check('package'),
    shop: check('shop'),
    autoRenewal: check('autoRenewal'),
    dataCenter: check('analytics') || check('pointRecords') || check('membershipStats'),
    analytics: check('analytics'),
    pointRecords: check('pointRecords'),
    membershipStats: check('membershipStats'),
    systemSettings: check('roles') || check('logs') || check('announcement') || check('currencyRate'),
    roles: check('roles'),
    logs: check('logs'),
    announcement: check('announcement'),
    currencyRate: check('currencyRate')
  }
})

function hasPermission(view) {
  return menuVisibility.value[view] !== false
}

const roleCodeSuggestion = computed(() => {
  const name = (roleForm.name || '').trim()
  if (!name) return ''
  if (roleCodePresets[name]) return roleCodePresets[name]
  for (const [key, code] of Object.entries(roleCodePresets)) {
    if (name.includes(key)) return code
  }
  return generateCodeFromName(name)
})

function generateCodeFromName(name) {
  const asciiMap = {
    '管理': 'ADMIN', '运营': 'OPERATOR', '用户': 'USER', '内容': 'CONTENT',
    '数据': 'DATA', '分析': 'ANALYST', '财务': 'FINANCE', '审计': 'AUDITOR',
    '订单': 'ORDER', '积分': 'POINT', '审核': 'REVIEW', '编辑': 'EDITOR',
    '查看': 'VIEWER', '访客': 'GUEST', '客服': 'SERVICE', '测试': 'TESTER',
    '超级': 'SUPER', '系统': 'SYSTEM', '日志': 'LOG', '全部': 'ALL',
    '只读': 'READONLY', '观察员': 'OBSERVER'
  }
  let result = ''
  for (const ch of name) {
    if (/[a-zA-Z0-9]/.test(ch)) {
      result += ch
    } else if (asciiMap[ch]) {
      result += asciiMap[ch] + '_'
    }
  }
  result = result.replace(/_+$/, '').replace(/__+/g, '_')
  if (!result) {
    let hash = 0
    for (const ch of name) hash = ((hash << 5) - hash + ch.charCodeAt(0)) | 0
    result = 'ROLE_' + Math.abs(hash).toString(36).toUpperCase().slice(0, 6)
  }
  return result.toUpperCase()
}

watch(() => roleForm.name, (newName) => {
  if (!newName) {
    lastAutoCode.value = ''
    return
  }
  const suggestion = roleCodeSuggestion.value
  if (suggestion && !roleForm.code || roleForm.code === lastAutoCode.value) {
    roleForm.code = suggestion
    lastAutoCode.value = suggestion
  }
})

const pageTitle = computed(() => ({
  dashboard: t('dashboard'),
  category: t('categoryConfig'),
  drama: t('dramaLibrary'),
  recommendations: t('recommendationsManagement'),
  episode: t('episodeManagement'),
  analytics: t('contentAnalytics'),
  users: t('userPool'),
  orders: t('orderManagement'),
  package: t('packageManagement'),
  roles: t('rolePermission'),
  logs: t('logs'),
  announcement: t('announcementManagement'),
  feedback: t('feedbackManagement'),
  profile: t('personalCenter'),
  membership: t('membershipManagement'),
  shop: t('shopManagement'),
  pointRecords: t('pointRecords'),
  autoRenewal: t('autoRenewalManagement'),
  membershipStats: t('membershipStats')
})[view.value])

const pageEyebrow = computed(() => ({
  dashboard: t('dataOverview'),
  category: t('contentManagement'),
  drama: t('contentManagement'),
  recommendations: t('recommendationsManagement'),
  episode: t('contentManagement'),
  analytics: t('dataCenter'),
  users: t('userOperations'),
  orders: t('tradeCenter'),
  package: t('tradeCenter'),
  roles: t('systemSettings'),
  logs: t('systemSettings'),
  announcement: t('systemSettings'),
  feedback: t('userOperations'),
  profile: t('systemSettings'),
  membership: t('userOperations'),
  shop: t('tradeCenter'),
  pointRecords: t('dataCenter'),
  autoRenewal: t('tradeCenter'),
  membershipStats: t('dataCenter')
})[view.value] || t('contentManagement'))

const maxTrendEvents = computed(() => {
  const trendData = view.value === 'dashboard' ? dashboardData.value.playTrend || [] : analytics.trend
  return Math.max(1, ...trendData.map(row => Number(field(row, 'playEvents', 'play_events')) || 0))
})

const contentTypeFilterOptions = computed(() => filterGroup('contentType')?.options || [])

const visibleCategoryFilterGroups = computed(() => categoryFilterGroups.value.filter(group => group.key !== 'contentType'))

const categoryGroupOptions = computed(() => categoryFilterGroups.value)

const categoryFilterOptionCount = computed(() => categoryFilterGroups.value.reduce(
  (total, group) => total + (group.options || []).filter(option => Number(option.status ?? 1) >= 0).length,
  0
))

function t(key) {
  return adminI18n.global.t(key)
}

function filterGroup(key) {
  return categoryFilterGroups.value.find(group => group.key === key)
}

function dramaFilterOptions(key) {
  return (filterGroup(key)?.options || []).filter(item => item.key && item.key !== 'all' && Number(item.status ?? 1) === 1)
}

function firstDramaFilterValue(key, fallback) {
  return dramaFilterOptions(key)[0]?.key || fallback
}

function filterGroupLabel(key) {
  const label = filterGroup(key)?.label
  return label ? streamT(label) : key
}

function filterOptionLabel(option) {
  return option?.label ? streamT(option.label) : option?.key || ''
}

function optionText(groupKey, optionKey) {
  const option = dramaFilterOptions(groupKey).find(item => item.key === optionKey)
  return option ? filterOptionLabel(option) : '-'
}

function enabledOptionCount(group) {
  return (group?.options || []).filter(option => Number(option.status ?? 1) === 1).length
}

function enabledCountText(group) {
  return `可用 ${enabledOptionCount(group)} 项`
}

function groupCategoryFilters(rows) {
  const groups = new Map()
  for (const row of rows || []) {
    const groupKey = field(row, 'groupKey', 'group_key')
    const optionKey = field(row, 'optionKey', 'option_key')
    if (!groupKey || !optionKey) continue
    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        key: groupKey,
        label: field(row, 'groupLabelKey', 'group_label_key') || groupKey,
        sortOrder: Number(field(row, 'groupSortOrder', 'group_sort_order')) || 0,
        options: []
      })
    }
    groups.get(groupKey).options.push({
      id: field(row, 'id'),
      key: optionKey,
      label: field(row, 'optionLabelKey', 'option_label_key') || optionKey,
      sortOrder: Number(field(row, 'optionSortOrder', 'option_sort_order')) || 0,
      status: Number(field(row, 'status') ?? 1),
      groupKey,
      groupLabelKey: field(row, 'groupLabelKey', 'group_label_key') || groupKey,
      groupSortOrder: Number(field(row, 'groupSortOrder', 'group_sort_order')) || 0,
      optionKey,
      optionLabelKey: field(row, 'optionLabelKey', 'option_label_key') || optionKey,
      optionSortOrder: Number(field(row, 'optionSortOrder', 'option_sort_order')) || 0
    })
  }
  return [...groups.values()]
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map(group => ({
      ...group,
      options: group.options.sort((left, right) => left.sortOrder - right.sortOrder)
    }))
}

function newCategoryFilterDraft(group = null) {
  const currentGroup = group || filterGroup('background')
  return {
    groupKey: currentGroup?.key || 'background',
    groupLabelKey: currentGroup?.label || 'category.background',
    groupSortOrder: currentGroup?.sortOrder ?? 20,
    optionKey: '',
    optionLabelKey: '',
    optionSortOrder: ((currentGroup?.options || []).length + 1) * 10,
    status: 1
  }
}

function toCategoryFilterDraft(group, option) {
  return {
    id: option?.id,
    groupKey: group?.key || option?.groupKey || '',
    groupLabelKey: group?.label || option?.groupLabelKey || '',
    groupSortOrder: group?.sortOrder ?? option?.groupSortOrder ?? 0,
    optionKey: option?.key || option?.optionKey || '',
    optionLabelKey: option ? filterOptionLabel(option) : '',
    optionSortOrder: option?.sortOrder ?? option?.optionSortOrder ?? 0,
    status: Number(option?.status ?? 1)
  }
}

function selectCategoryGroup(key) {
  const group = filterGroup(key)
  if (!group) return
  categoryForm.groupLabelKey = group.label
  categoryForm.groupSortOrder = group.sortOrder
}

function categoryPayload() {
  const groupKey = nullableText(categoryForm.groupKey)
  const optionLabelKey = nullableText(categoryForm.optionLabelKey)
  if (!groupKey) throw new Error(t('categoryGroupRequired'))
  if (!optionLabelKey) throw new Error(t('categoryOptionRequired'))
  const group = filterGroup(groupKey)
  return {
    id: categoryForm.id,
    groupKey,
    groupLabelKey: group?.label || categoryForm.groupLabelKey || groupKey,
    groupSortOrder: group?.sortOrder ?? categoryForm.groupSortOrder ?? 0,
    optionKey: categoryForm.optionKey || `custom_${Date.now().toString(36)}`,
    optionLabelKey,
    optionSortOrder: integerNumber(categoryForm.optionSortOrder, 0),
    status: Number(categoryForm.status ?? 1)
  }
}

function setLocale(value) {
  const normalized = normalizeLocale(value)
  adminI18n.global.locale.value = normalized
  document.documentElement.lang = normalized
  applyLocaleSideEffects(normalized)
  document.title = t('appName')
}

async function setStreamLocale(value) {
  const normalized = normalizeLocale(value)
  await loadStreamLocaleMessages(normalized)
  streamLocale.value = normalized
  applyLocaleSideEffects(normalized)
  document.title = streamT('auth.appName')
}

async function loadPublicStats() {
  try {
    const data = await api.publicStats()
    publicStats.value = data
  } catch (err) {
    // 静默忽略，使用默认值
  }
}

onMounted(async () => {
  window.addEventListener('duanju:auth-expired', handleExpiredAuth)
  window.addEventListener('resize', handleChartResize)
  const savedAdminToken = sessionStorage.getItem('adminToken') || ''
  const savedUserToken = sessionStorage.getItem('userToken') || ''
  try {
    if (savedAdminToken) {
      token.value = savedAdminToken
      await fetchAdminPermissions()
      await api.adminMe()
      setLocale('zh-CN')
      await loadAll()
    } else if (savedUserToken) {
      userToken.value = savedUserToken
      const user = await api.userMe()
      sessionStorage.setItem('user', JSON.stringify(user))
      await setStreamLocale(streamLocale.value)
    }
  } catch (err) {
    clearAuthSession()
    authError.value = err.message
  } finally {
    authReady.value = true
    if (!token.value && !userToken.value) {
      await setStreamLocale(streamLocale.value)
      await loadPublicStats()
      await loadAuthCaptcha()
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('duanju:auth-expired', handleExpiredAuth)
  window.removeEventListener('resize', handleChartResize)
  stopTranscodePolling()
  if (videoPreviewHls) {
    videoPreviewHls.destroy()
    videoPreviewHls = null
  }
  trendChartInstance?.dispose()
  dramaChartInstance?.dispose()
  viewerActivityChartInstance?.dispose()
  userSegmentChartInstance?.dispose()
  episodeCompletionChartInstance?.dispose()
  trendChartInstance = null
  dramaChartInstance = null
  viewerActivityChartInstance = null
  userSegmentChartInstance = null
  episodeCompletionChartInstance = null
})

watch(authMode, () => {
  loginForm.captchaCode = ''
  loadAuthCaptcha()
})

watch(loginRole, () => {
  loginForm.captchaCode = ''
  authError.value = ''
  loadAuthCaptcha()
})

function toggleAuthMode() {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  authError.value = ''
}

async function submitAuth() {
  const account = loginForm.account.trim()
  authError.value = ''
  if (!account || !loginForm.password || !loginForm.captchaCode) {
    authError.value = streamT('auth.accountPasswordRequired')
    ElMessage.warning(authError.value)
    return
  }
  if (authLoading.value) return
  authLoading.value = true
  try {
    let data
    if (authMode.value === 'register') {
      if (loginRole.value === 'admin') {
        authError.value = streamT('auth.registerDisabledForAdmin')
        ElMessage.warning(authError.value)
        return
      }
      data = await api.register({ username: account, password: loginForm.password, nickname: loginForm.nickname.trim(), captchaId: loginForm.captchaId, captchaCode: loginForm.captchaCode })
      authMode.value = 'login'
      loginForm.password = ''
      loginForm.nickname = ''
      loginForm.captchaCode = ''
      ElMessage.success(streamT('auth.registerSuccess'))
      await loadAuthCaptcha()
      return
    }
    const payload = { username: account, password: loginForm.password, captchaId: loginForm.captchaId, captchaCode: loginForm.captchaCode }
    if (loginRole.value === 'admin') {
      data = await api.adminLogin(payload)
      if (data.role === 'ADMIN' || data.admin) {
        sessionStorage.setItem('adminToken', data.token)
        sessionStorage.setItem('adminRefreshToken', data.refreshToken)
        sessionStorage.removeItem('userToken')
        sessionStorage.removeItem('userRefreshToken')
        token.value = data.token
        userToken.value = ''
        setLocale('zh-CN')
        await fetchAdminPermissions()
        await loadAll()
        return
      }
      authError.value = streamT('auth.adminOnly')
      ElMessage.error(authError.value)
      await loadAuthCaptcha()
      return
    }
    data = await api.login(payload)
    if (data.role === 'ADMIN' || data.admin) {
      sessionStorage.setItem('adminToken', data.token)
      sessionStorage.setItem('adminRefreshToken', data.refreshToken)
      sessionStorage.removeItem('userToken')
      sessionStorage.removeItem('userRefreshToken')
      token.value = data.token
      userToken.value = ''
      setLocale('zh-CN')
      await loadAll()
      return
    }
    redirectUser(data)
  } catch (err) {
    await loadAuthCaptcha()
    if (String(err.message).includes('已注册')) {
      authMode.value = 'login'
    }
    authError.value = err.message
    ElMessage.error(err.message)
  } finally {
    authLoading.value = false
  }
}

async function loadAuthCaptcha() {
  captchaLoading.value = true
  try {
    const scene = authMode.value === 'register'
      ? 'REGISTER'
      : (loginRole.value === 'admin' ? 'ADMIN_LOGIN' : 'LOGIN')
    const data = await api.captcha({ scene })
    loginForm.captchaId = data.captchaId
    captchaImage.value = data.imageData
  } catch (err) {
    captchaImage.value = ''
    authError.value = err.message
  } finally {
    captchaLoading.value = false
  }
}

function redirectUser(data) {
  sessionStorage.setItem('userToken', data.token)
  sessionStorage.setItem('userRefreshToken', data.refreshToken)
  sessionStorage.removeItem('adminToken')
  sessionStorage.removeItem('adminRefreshToken')
  token.value = ''
  userToken.value = data.token
  if (data.user) {
    sessionStorage.setItem('user', JSON.stringify(data.user))
  }
}

function logout() {
  sessionStorage.removeItem('adminToken')
  sessionStorage.removeItem('adminRefreshToken')
  sessionStorage.removeItem('adminPermissions')
  token.value = ''
  adminPermissions.value = []
  setStreamLocale(streamLocale.value)
}

function logoutUser() {
  sessionStorage.removeItem('userToken')
  sessionStorage.removeItem('userRefreshToken')
  sessionStorage.removeItem('user')
  userToken.value = ''
  setStreamLocale(streamLocale.value)
}

function clearAuthSession() {
  for (const key of ['adminToken', 'adminRefreshToken', 'userToken', 'userRefreshToken', 'user']) {
    sessionStorage.removeItem(key)
  }
  token.value = ''
  userToken.value = ''
}

function handleExpiredAuth() {
  clearAuthSession()
  setStreamLocale(streamLocale.value)
  loadAuthCaptcha()
}

async function loadAll() {
  try {
    const categoryFilterRows = await api.adminCategoryFilters()
    categoryFilterGroups.value = groupCategoryFilters(categoryFilterRows)
    await loadDramas(false)
    if (!dramas.value.some(item => item.id === episodeDramaId.value)) {
      episodeDramaId.value = dramas.value[0]?.id || null
    }
    await loadEpisodes()
    await loadDashboard()
    if (view.value === 'analytics') {
      await loadAnalytics()
    }
  } catch (err) {
    ElMessage.error(err.message)
  }
}

async function loadDramas(showError = true) {
  try {
    dramas.value = await api.dramas(cleanParams(dramaFilters))
  } catch (err) {
    if (showError) {
      ElMessage.error(err.message)
    }
  }
}

async function loadEpisodes() {
  try {
    episodes.value = episodeDramaId.value ? await api.episodes(episodeDramaId.value, cleanParams(episodeFilters)) : []
  } catch (err) {
    episodes.value = []
    ElMessage.error(err.message)
  }
}

function selectView(next) {
  if (!hasPermission(next)) {
    const firstAvailableView = Object.entries(viewPermissionMap).find(([view]) => hasPermission(view))?.[0]
    next = firstAvailableView || 'dashboard'
  }
  view.value = next
  if (next === 'dashboard') {
    loadDashboard()
  } else if (next === 'analytics') {
    loadAnalytics()
  } else if (next === 'users') {
    loadUsers()
  } else if (next === 'orders') {
    loadOrders()
  } else if (next === 'roles') {
    loadRolePage()
  } else if (next === 'logs') {
    loadLogs()
  } else if (next === 'announcement') {
    loadAnnouncements()
  } else if (next === 'feedback') {
    loadFeedbacks()
  }
}

async function fetchAdminPermissions() {
  try {
    const data = await api.adminMe()
    adminPermissions.value = data.permissions || []
    sessionStorage.setItem('adminPermissions', JSON.stringify(adminPermissions.value))
    if (adminPermissions.value.length === 0 || !hasPermission(view.value)) {
      view.value = 'dashboard'
    }
  } catch (err) {
    adminPermissions.value = []
  }
}

async function loadDashboard() {
  analyticsLoading.value = true
  try {
    const data = await api.adminDashboard({ days: analyticsDays.value })
    dashboardData.value = data || { kpis: {}, dramaRanking: [], playTrend: [], recentOrders: [] }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    analyticsLoading.value = false
  }
}

async function loadAnalytics() {
  analyticsLoading.value = true
  try {
    const params = { days: analyticsDays.value }
    const scopedParams = {
      ...params,
      dramaId: analyticsDramaId.value || undefined
    }
    const [overview, dramasData, episodesData, trendData, viewersData, reachData] = await Promise.all([
      api.analyticsOverview(),
      api.analyticsDramas(params),
      api.analyticsEpisodes(scopedParams),
      api.analyticsTrend(params),
      api.analyticsViewers({ limit: 50 }),
      analyticsDramaId.value ? api.analyticsEpisodeReach({ dramaId: analyticsDramaId.value }) : Promise.resolve([])
    ])
    analytics.overview = overview || {}
    analytics.dramas = dramasData || []
    analytics.episodes = episodesData || []
    analytics.trend = trendData || []
    analytics.viewers = viewersData || []
    analytics.reach = reachData || []
    await nextTick()
    initCharts()
    updateCharts()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    analyticsLoading.value = false
  }
}

function initCharts() {
  if (trendChartRef.value && !trendChartInstance) {
    trendChartInstance = echarts.init(trendChartRef.value, 'dark')
  }
  if (dramaChartRef.value && !dramaChartInstance) {
    dramaChartInstance = echarts.init(dramaChartRef.value, 'dark')
  }
  if (viewerActivityChartRef.value && !viewerActivityChartInstance) {
    viewerActivityChartInstance = echarts.init(viewerActivityChartRef.value, 'dark')
  }
  if (userSegmentChartRef.value && !userSegmentChartInstance) {
    userSegmentChartInstance = echarts.init(userSegmentChartRef.value, 'dark')
  }
  if (episodeCompletionChartRef.value && !episodeCompletionChartInstance) {
    episodeCompletionChartInstance = echarts.init(episodeCompletionChartRef.value, 'dark')
  }
}

function updateCharts() {
  if (trendChartInstance) {
    const trendData = analytics.trend.map(row => ({
      date: field(row, 'statDate', 'stat_date'),
      plays: Number(field(row, 'playEvents', 'play_events')) || 0
    }))
    trendChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(23, 21, 19, 0.9)',
        borderColor: '#d4af68',
        textStyle: { color: '#fff' }
      },
      grid: { left: '10%', right: '5%', top: '10%', bottom: '15%' },
      xAxis: {
        type: 'category',
        data: trendData.map(d => d.date ? d.date.slice(5) : ''),
        axisLine: { lineStyle: { color: '#d4af68' } },
        axisLabel: { color: '#999' }
      },
      yAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: '#d4af68' } },
        splitLine: { lineStyle: { color: 'rgba(212, 175, 104, 0.1)' } },
        axisLabel: { color: '#999' }
      },
      series: [{
        name: '播放次数',
        type: 'line',
        data: trendData.map(d => d.plays),
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(212, 175, 104, 0.6)' },
            { offset: 1, color: 'rgba(212, 175, 104, 0.05)' }
          ])
        },
        lineStyle: { color: '#d4af68', width: 2 },
        itemStyle: { color: '#d4af68' },
        symbol: 'circle',
        symbolSize: 8
      }]
    })
  }
  if (dramaChartInstance) {
    const dramaData = analytics.dramas.slice(0, 10).map(row => ({
      name: field(row, 'dramaTitle', 'title', 'drama_title') || '未知',
      plays: Number(field(row, 'playEvents', 'play_events')) || 0
    }))
    dramaChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(23, 21, 19, 0.9)',
        borderColor: '#d4af68',
        textStyle: { color: '#fff' }
      },
      grid: { left: '3%', right: '10%', top: '5%', bottom: '5%', containLabel: true },
      xAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: '#d4af68' } },
        splitLine: { lineStyle: { color: 'rgba(212, 175, 104, 0.1)' } },
        axisLabel: { color: '#999' }
      },
      yAxis: {
        type: 'category',
        data: dramaData.map(d => d.name),
        axisLine: { lineStyle: { color: '#d4af68' } },
        axisLabel: { color: '#999', width: 100, overflow: 'truncate' }
      },
      series: [{
        type: 'bar',
        data: dramaData.map(d => d.plays),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#c86d26' },
            { offset: 1, color: '#d4af68' }
          ]),
          borderRadius: [0, 4, 4, 0]
        },
        barWidth: '60%'
      }]
    })
  }
  if (viewerActivityChartInstance) {
    const trendData = analytics.trend.map(row => ({
      date: field(row, 'statDate', 'stat_date'),
      viewers: Number(field(row, 'viewers', 'activeViewers', 'active_viewers')) || 0
    }))
    viewerActivityChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(23, 21, 19, 0.9)',
        borderColor: '#d4af68',
        textStyle: { color: '#fff' }
      },
      grid: { left: '10%', right: '5%', top: '10%', bottom: '15%' },
      xAxis: {
        type: 'category',
        data: trendData.map(d => d.date ? d.date.slice(5) : ''),
        axisLine: { lineStyle: { color: '#d4af68' } },
        axisLabel: { color: '#999' }
      },
      yAxis: {
        type: 'value',
        axisLine: { lineStyle: { color: '#d4af68' } },
        splitLine: { lineStyle: { color: 'rgba(212, 175, 104, 0.1)' } },
        axisLabel: { color: '#999' }
      },
      series: [{
        name: '活跃用户',
        type: 'line',
        data: trendData.map(d => d.viewers),
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(47, 111, 237, 0.5)' },
            { offset: 1, color: 'rgba(47, 111, 237, 0.05)' }
          ])
        },
        lineStyle: { color: '#2f6fed', width: 2 },
        itemStyle: { color: '#2f6fed' },
        symbol: 'circle',
        symbolSize: 6
      }]
    })
  }
  if (userSegmentChartInstance) {
    const segments = computeUserSegments()
    userSegmentChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(23, 21, 19, 0.9)',
        borderColor: '#d4af68',
        textStyle: { color: '#fff' },
        formatter: '{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'horizontal',
        bottom: '5%',
        textStyle: { color: '#999' }
      },
      series: [{
        name: t('userSegmentation'),
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#171513',
          borderWidth: 2
        },
        label: {
          show: false
        },
        data: segments
      }]
    })
  }
  if (episodeCompletionChartInstance) {
    const epData = computeEpisodeCompletionData()
    episodeCompletionChartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(23, 21, 19, 0.9)',
        borderColor: '#d4af68',
        textStyle: { color: '#fff' }
      },
      legend: {
        data: [t('playCount'), t('completionRate')],
        textStyle: { color: '#999' },
        top: 0
      },
      grid: { left: '10%', right: '10%', top: '15%', bottom: '5%' },
      xAxis: {
        type: 'category',
        data: epData.map(d => d.name),
        axisLine: { lineStyle: { color: '#d4af68' } },
        axisLabel: { color: '#999', rotate: 45, fontSize: 10 }
      },
      yAxis: [
        {
          type: 'value',
          name: t('playCount'),
          axisLine: { lineStyle: { color: '#d4af68' } },
          splitLine: { lineStyle: { color: 'rgba(212, 175, 104, 0.1)' } },
          axisLabel: { color: '#999' }
        },
        {
          type: 'value',
          name: t('completionRate'),
          min: 0,
          max: 100,
          axisLine: { lineStyle: { color: '#2f6fed' } },
          splitLine: { show: false },
          axisLabel: { color: '#999', formatter: '{value}%' }
        }
      ],
      series: [
        {
          name: t('playCount'),
          type: 'bar',
          data: epData.map(d => d.plays),
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#d4af68' },
              { offset: 1, color: 'rgba(212, 175, 104, 0.3)' }
            ]),
            borderRadius: [4, 4, 0, 0]
          },
          barWidth: '40%'
        },
        {
          name: t('completionRate'),
          type: 'line',
          yAxisIndex: 1,
          data: epData.map(d => d.completionRate),
          smooth: true,
          lineStyle: { color: '#2f6fed', width: 2 },
          itemStyle: { color: '#2f6fed' },
          symbol: 'circle',
          symbolSize: 6
        }
      ]
    })
  }
}

function handleChartResize() {
  trendChartInstance?.resize()
  dramaChartInstance?.resize()
  viewerActivityChartInstance?.resize()
  userSegmentChartInstance?.resize()
  episodeCompletionChartInstance?.resize()
}

function computeUserSegments() {
  const viewers = analytics.viewers || []
  const total = viewers.length || 1
  const segments = [
    {
      name: t('activeUsers'),
      value: viewers.filter(v => Number(field(v, 'watchedDramas', 'watched_dramas')) >= 5).length
    },
    {
      name: t('casualUsers'),
      value: viewers.filter(v => {
        const count = Number(field(v, 'watchedDramas', 'watched_dramas'))
        return count >= 2 && count < 5
      }).length
    },
    {
      name: t('newUsers'),
      value: viewers.filter(v => Number(field(v, 'watchedDramas', 'watched_dramas')) <= 1).length
    }
  ]
  return segments.filter(s => s.value > 0)
}

function computeEpisodeCompletionData() {
  const episodes = (analytics.episodes || []).slice(0, 15)
  return episodes.map(ep => {
    const plays = Number(field(ep, 'playEvents', 'play_events')) || 0
    const avgProgress = Number(field(ep, 'avgProgressSeconds', 'avg_progress_seconds')) || 0
    const duration = Number(field(ep, 'durationSeconds', 'duration_seconds')) || 1
    const completionRate = duration > 0 ? Math.min(100, Math.round((avgProgress / duration) * 100)) : 0
    const title = field(ep, 'episodeNo', 'episode_no') || field(ep, 'episodeTitle', 'title', 'episode_title') || '-'
    return {
      name: typeof title === 'number' ? `第${title}集` : String(title).slice(0, 8),
      plays,
      completionRate
    }
  })
}

async function loadUsers() {
  await runAction(async () => {
    users.value = await api.users(cleanParams({ ...userFilters, limit: 100 }))
  })
}

async function changeUserStatus(row, status) {
  await runAction(async () => {
    await api.updateUserStatus(row.id, { status })
    await loadUsers()
  })
}

function openPoints(row) {
  selectedUser.value = row
  copyTo(pointsForm, { delta: 100, remark: '' })
  pointsDialog.value = true
}

async function savePoints() {
  await runAction(async () => {
    const delta = Number(pointsForm.delta)
    if (!Number.isInteger(delta) || delta === 0) throw new Error(t('pointsDeltaRequired'))
    await api.adjustUserPoints(selectedUser.value.id, {
      delta,
      remark: nullableText(pointsForm.remark)
    })
    pointsDialog.value = false
    ElMessage.success(t('pointGrantSuccess'))
    await loadUsers()
  })
}

function onRoleNameInput() {
  if (roleForm.name && (!roleForm.code || roleForm.code === lastAutoCode.value)) {
    const suggestion = roleCodeSuggestion.value
    if (suggestion) {
      roleForm.code = suggestion
      lastAutoCode.value = suggestion
    }
  }
}

function applyCodeSuggestion() {
  if (roleCodeSuggestion.value) {
    roleForm.code = roleCodeSuggestion.value
    lastAutoCode.value = roleCodeSuggestion.value
  }
}

function onPermissionCheck() {
  if (permissionTreeRef.value) {
    const keys = permissionTreeRef.value.getCheckedKeys(true)
    const leafKeys = keys.filter(k => allPermissionCodes.value.includes(k) || k === '*')
    checkedPermissionKeys.value = [...new Set([...checkedPermissionKeys.value.filter(k => !permissionCatalog.some(g => g.group === k)), ...leafKeys])]
  }
}

function checkAllPermissions() {
  if (permissionTreeRef.value) {
    const leafCodes = allPermissionCodes.value
    checkedPermissionKeys.value = [...leafCodes]
    nextTick(() => {
      if (permissionTreeRef.value) {
        permissionTreeRef.value.setCheckedKeys(leafCodes)
      }
    })
  }
}

function clearAllPermissions() {
  checkedPermissionKeys.value = []
  nextTick(() => {
    if (permissionTreeRef.value) {
      permissionTreeRef.value.setCheckedKeys([])
    }
  })
}

function getPermLabel(code) {
  for (const group of permissionCatalog) {
    for (const item of group.items) {
      if (item.code === code) return item.label
    }
  }
  return code
}

function getPermTagType(code) {
  const typeMap = {
    'dashboard:view': 'warning',
    'content:manage': 'success',
    'user:manage': '',
    'point:manage': 'warning',
    'order:manage': 'success',
    'role:manage': 'danger',
    'analytics:view': 'info',
    'log:view': 'info',
    '*': 'danger'
  }
  return typeMap[code] || ''
}

function openBatchUpload() {
  if (batchUploading.value) {
    batchDialog.value = true
    return
  }
  batchForm.startEpisodeNo = (selectedDramaEpisodes.value || 0) + 1
  batchForm.episodes = []
  batchForm.files = []
  batchFileList.value = []
  batchMode.value = 'local'
  vpsFiles.value = []
  vpsScanning.value = false
  resetVpsTask()
  stopVpsPolling()
  batchDialog.value = true
}

function resetVpsTask() {
  vpsTask.running = false
  vpsTask.status = 'none'
  vpsTask.total = 0
  vpsTask.processed = 0
  vpsTask.success = 0
  vpsTask.fail = 0
  vpsTask.elapsedMillis = 0
  vpsTask.files = []
}

function stopVpsPolling() {
  if (vpsPollingTimer) {
    clearInterval(vpsPollingTimer)
    vpsPollingTimer = null
  }
}

async function scanVpsFiles() {
  if (!selectedDrama.value) {
    ElMessage.warning(t('selectDramaFirst'))
    return
  }
  vpsScanning.value = true
  resetVpsTask()
  try {
    const data = await api.scanBatchUpload(selectedDrama.value.id)
    vpsFiles.value = data || []
    if (!vpsFiles.value.length) {
      ElMessage.info(t('noFilesScanned'))
    } else {
      ElMessage.success(`${vpsFiles.value.length} ${t('selectedEpisodes')}`)
    }
  } catch (err) {
    ElMessage.error(err.message || t('scanFiles'))
  } finally {
    vpsScanning.value = false
  }
}

async function startVpsUpload() {
  if (!selectedDrama.value) {
    ElMessage.warning(t('selectDramaFirst'))
    return
  }
  if (!vpsFiles.value.length) {
    ElMessage.warning(t('noFilesScanned'))
    return
  }
  vpsTask.running = true
  vpsTask.status = 'running'
  vpsTask.total = vpsFiles.value.length
  vpsTask.processed = 0
  vpsTask.success = 0
  vpsTask.fail = 0
  vpsTask.files = vpsFiles.value.map(f => ({ ...f, status: 'pending', episodeNo: null, error: null }))

  try {
    await api.startBatchUpload(selectedDrama.value.id, batchForm.startEpisodeNo)
    ElMessage.success(t('batchUploadRunning'))
    startVpsPolling()
  } catch (err) {
    vpsTask.running = false
    vpsTask.status = 'error'
    ElMessage.error(err.message || t('batchCreateFailed'))
  }
}

function startVpsPolling() {
  stopVpsPolling()
  vpsPollingTimer = setInterval(async () => {
    try {
      const data = await api.batchUploadStatus(selectedDrama.value.id)
      if (!data || data.status === 'none') return
      vpsTask.status = data.status
      vpsTask.total = data.totalFiles || 0
      vpsTask.processed = data.processed || 0
      vpsTask.success = data.success || 0
      vpsTask.fail = data.fail || 0
      vpsTask.elapsedMillis = data.elapsedMillis || 0
      vpsTask.files = data.files || []
      if (data.status === 'done' || data.status === 'error') {
        vpsTask.running = false
        stopVpsPolling()
        if (data.status === 'done' && data.fail === 0) {
          ElMessage.success(t('batchUploadDone'))
        } else if (data.fail > 0) {
          ElMessage.warning(`${t('batchUploadFailed')} ${data.fail}/${data.totalFiles}`)
        }
        await loadEpisodes()
      }
    } catch (err) {
      console.error('poll batch upload status failed', err)
    }
  }, 3000)
}

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let val = bytes
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024
    i++
  }
  return val.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

const vpsTaskPercentage = computed(() => {
  if (!vpsTask.total) return 0
  return Math.round((vpsTask.processed / vpsTask.total) * 100)
})

const vpsTaskProgressStatus = computed(() => {
  if (vpsTask.status === 'error') return 'exception'
  if (vpsTask.status === 'done' && vpsTask.fail > 0) return 'warning'
  if (vpsTask.status === 'done') return 'success'
  return ''
})

const vpsTaskTagType = computed(() => {
  if (vpsTask.status === 'running') return 'primary'
  if (vpsTask.status === 'done' && vpsTask.fail > 0) return 'warning'
  if (vpsTask.status === 'done') return 'success'
  if (vpsTask.status === 'error') return 'danger'
  return 'info'
})

const vpsTaskStatusLabel = computed(() => {
  if (vpsTask.status === 'running') return t('batchUploadRunning')
  if (vpsTask.status === 'done' && vpsTask.fail > 0) return t('batchUploadFailed')
  if (vpsTask.status === 'done') return t('batchUploadDone')
  if (vpsTask.status === 'error') return t('batchUploadFailed')
  return ''
})

function vpsFileTagType(status) {
  if (status === 'success') return 'success'
  if (status === 'fail') return 'danger'
  if (status === 'uploading') return 'primary'
  if (status === 'skipped') return 'info'
  return 'info'
}

function handleBatchFileChange(file) {
  if (file.status === 'ready') {
    batchFileList.value = [...batchFileList.value, file]
    const idx = batchForm.episodes.length
    const epNo = batchForm.startEpisodeNo + idx
    const baseName = file.name.replace(/\.[^.]+$/, '')
    const ep = reactive({
      file,
      name: file.name,
      episodeNo: epNo,
      title: baseName,
      videoUrl: null,
      pricePoints: 10,
      accessType: 'POINTS',
      uploadStatus: 'pending',
      progress: 0,
      errorReason: null,
      durationSeconds: null
    })
    batchForm.episodes.push(ep)
    const rawFile = file.raw || file
    if (rawFile) {
      const video = document.createElement('video')
      video.preload = 'metadata'
      video.onloadedmetadata = () => {
        if (video.duration && video.duration > 0) {
          ep.durationSeconds = Math.round(video.duration)
        }
        URL.revokeObjectURL(video.src)
      }
      video.src = URL.createObjectURL(rawFile)
    }
  }
}

function handleBatchFileRemove(file) {
  batchForm.episodes = batchForm.episodes.filter(ep => ep.name !== file.name)
  batchFileList.value = batchFileList.value.filter(f => f.uid !== file.uid)
  reindexBatchEpisodes()
}

function removeBatchEpisode(idx) {
  batchForm.episodes.splice(idx, 1)
  reindexBatchEpisodes()
}

function reindexBatchEpisodes() {
  batchForm.episodes.forEach((ep, idx) => {
    ep.episodeNo = batchForm.startEpisodeNo + idx
  })
}

function clearBatchList() {
  batchForm.episodes = []
  batchForm.files = []
  batchFileList.value = []
}

const BATCH_CONCURRENCY = 3

async function uploadSingleEpisode(ep) {
  ep.uploadStatus = 'uploading'
  ep.progress = 0
  ep.errorReason = null
  ep.storageProvider = 'r2'
  ep.cloudflareUid = null
  ep._controller = new AbortController()
  if (ep._pendingRef) ep._pendingRef._controller = ep._controller

  let lastTime = Date.now()
  let lastLoaded = 0
  const onProgress = (e) => {
    if (e.lengthComputable) {
      ep.progress = Math.min(99, Math.round((e.loaded / e.total) * 100))
      const now = Date.now()
      const dt = (now - lastTime) / 1000
      const dl = e.loaded - lastLoaded
      ep.speed = dt > 0 ? dl / dt : 0
      lastTime = now
      lastLoaded = e.loaded
      if (ep._pendingRef) {
        ep._pendingRef.progress = ep.progress
        ep._pendingRef.speed = ep.speed
      }
    }
  }
  const rawFile = ep.file?.raw || ep.file
  if (!rawFile) throw new Error('无法读取文件,请重新选择后再试')

  const presign = await api.presignStorage(rawFile.name, rawFile.type || 'video/mp4', 'video')
  if (!presign || !presign.supported || !presign.presignedUrl) {
    const supported = presign && presign.supported
    throw new Error(supported === false
      ? 'R2 未启用或配置不完整。请检查服务器 .env 中 R2_ENABLED=true 及 CLOUDFLARE_R2_* 相关变量。'
      : '预签名接口返回异常,请检查后端日志。')
  }
  await api.putToPresignedUrl(presign.presignedUrl, rawFile, presign.headers, onProgress, ep._controller.signal)
  ep.videoUrl = presign.url
  ep.storageProvider = presign.storageProvider || 'r2'
  ep.cloudflareUid = null
  if (typeof presign.durationSeconds === 'number' && ep.durationSeconds !== undefined) {
    ep.durationSeconds = presign.durationSeconds
  }
  ep.progress = 100
  ep.uploadStatus = 'success'
}

async function uploadEpisodesConcurrently(episodes) {
  const queue = episodes.map((_, i) => i)
  async function worker() {
    while (queue.length) {
      const idx = queue.shift()
      if (!batchForm.episodes[idx] || batchForm.episodes[idx].uploadStatus === 'success') continue
      try {
        await uploadSingleEpisode(batchForm.episodes[idx])
      } catch (err) {
        const ep = batchForm.episodes[idx]
        if (err.message === 'UPLOAD_CANCELLED') {
          ep.uploadStatus = 'fail'
          ep.errorReason = '已取消'
        } else {
          ep.uploadStatus = 'fail'
          ep.errorReason = resolveUploadError(err)
        }
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(BATCH_CONCURRENCY, episodes.length) }, () => worker()))
}

async function submitBatchUpload() {
  if (!selectedDrama.value) {
    ElMessage.warning(t('selectDramaFirst'))
    return
  }
  const dramaId = selectedDrama.value.id
  const toUpload = batchForm.episodes.filter(ep => ep.uploadStatus !== 'success')

  for (const ep of toUpload) {
    const pe = reactive({
      _id: Date.now() + Math.random() * 1000 + ep.episodeNo,
      _status: 'uploading',
      _controller: null,
      _file: ep.file,
      _dramaId: dramaId,
      _form: {},
      progress: 0,
      speed: 0,
      errorReason: null,
      episodeNo: ep.episodeNo,
      title: ep.title,
      dramaName: field(selectedDrama.value, 'title') || '',
      fileName: ep.file?.name || ep.file?.raw?.name || '',
      fileSize: ep.file?.size || ep.file?.raw?.size || 0,
      durationSeconds: ep.durationSeconds || null,
      coverUrl: null,
      coverObjectKey: null,
      pricePoints: ep.pricePoints,
      accessType: ep.accessType,
      storageProvider: 'r2'
    })
    pendingEpisodes.push(pe)
    ep._pendingRef = pe
  }

  batchDialog.value = false
  batchUploading.value = true

  await uploadEpisodesConcurrently(toUpload)

  for (const ep of toUpload) {
    const pe = ep._pendingRef
    if (!pe) continue
    if (ep.uploadStatus === 'success' && ep.videoUrl) {
      pe._form.videoUrl = ep.videoUrl
      pe._status = 'creating'
      pe.progress = 100
      await createPendingEpisode(pe)
    } else {
      pe._status = 'fail'
      pe.errorReason = ep.errorReason || '上传失败'
    }
  }

  batchUploading.value = false
  batchForm.episodes = []
  batchFileList.value = []
  await loadEpisodes()
}

async function retryBatchEpisode(idx) {
  const ep = batchForm.episodes[idx]
  if (!ep || ep.uploadStatus === 'uploading' || ep.uploadStatus === 'success') return
  ep.uploadStatus = 'uploading'
  ep.progress = 0
  ep.errorReason = null
  try {
    await uploadSingleEpisode(ep)
    ElMessage.success(`第 ${ep.episodeNo} 集上传成功`)
  } catch (err) {
    ep.uploadStatus = 'fail'
    ep.errorReason = resolveUploadError(err)
    ElMessage.error(`第 ${ep.episodeNo} 集重传失败：${resolveUploadError(err)}`)
  }
}

async function retryAllFailed() {
  const failed = batchForm.episodes.filter(ep => ep.uploadStatus === 'fail')
  if (!failed.length) return
  batchUploading.value = true
  await uploadEpisodesConcurrently(failed)
  batchUploading.value = false
  const stillFailed = batchForm.episodes.filter(ep => ep.uploadStatus === 'fail').length
  const recovered = failed.length - stillFailed
  if (stillFailed === 0) {
    ElMessage.success(`全部 ${failed.length} 集重传成功`)
  } else {
    ElMessage.warning(`重传完成：${recovered} 集成功，${stillFailed} 集仍失败`)
  }
}

function resolveUploadError(err) {
  if (!err) return t('uploadFailed')
  if (err.code === 'ECONNABORTED') return t('error.timeout') || '请求超时，请检查网络后重试'
  if (err.response?.status === 413) return '文件太大，超过服务器限制'
  if (err.response?.status === 401) return '登录已过期，请重新登录'
  if (err.response?.status === 403) return '没有上传权限'
  if (err.response?.status >= 500) return '服务器内部错误，请查看后端日志'
  if (!err.response && err.message === 'Network Error') return '网络连接失败，请检查域名和服务器是否正常'
  return err.message || String(err)
}

function batchEpisodeTagType(status) {
  if (status === 'uploading') return 'primary'
  if (status === 'success') return 'success'
  if (status === 'fail') return 'danger'
  return 'info'
}

function batchEpisodeStatusLabel(status) {
  if (status === 'uploading') return t('uploading')
  if (status === 'success') return t('uploadSuccess')
  if (status === 'fail') return t('uploadFailed')
  return ''
}

const selectedDramaEpisodes = computed(() => episodes.value.length)

function openUserDialog(row) {
  if (row) {
    copyTo(userForm, {
      id: row.id,
      username: field(row, 'username'),
      phone: field(row, 'phone'),
      password: '',
      nickname: field(row, 'nickname'),
      avatarUrl: field(row, 'avatar_url', 'avatarUrl'),
      points: Number(field(row, 'points')) || 0,
      statusSwitch: Number(field(row, 'status')) === 1 ? 1 : 0
    })
  } else {
    copyTo(userForm, {
      id: null,
      username: '',
      phone: '',
      password: '',
      nickname: '',
      avatarUrl: '',
      points: 0,
      statusSwitch: 1
    })
  }
  userDialog.value = true
}

async function saveUser() {
  await runAction(async () => {
    if (userFormRef.value) {
      await userFormRef.value.validate()
    }
    if (userForm.id) {
      await api.updateUser(userForm.id, {
        username: userForm.username,
        phone: userForm.phone,
        nickname: userForm.nickname,
        avatarUrl: userForm.avatarUrl,
        status: userForm.statusSwitch
      })
      ElMessage.success(t('userUpdated'))
    } else {
      await api.createUser({
        username: userForm.username,
        phone: userForm.phone,
        password: userForm.password || null,
        nickname: userForm.nickname,
        avatarUrl: userForm.avatarUrl,
        points: userForm.points
      })
      ElMessage.success(t('userCreated'))
    }
    userDialog.value = false
    await loadUsers()
  })
}

async function confirmDeleteUser(row) {
  try {
    await ElMessageBox.confirm(
      t('deleteUserConfirm', { name: field(row, 'username') || field(row, 'nickname') || row.id }),
      t('deleteConfirm'),
      { type: 'warning', confirmButtonText: t('delete'), cancelButtonText: t('cancel') }
    )
    await runAction(async () => {
      await api.deleteUser(row.id)
      ElMessage.success(t('userDeleted'))
      await loadUsers()
    })
  } catch (_) { /* cancelled */ }
}

async function loadOrders() {
  await runAction(async () => {
    orders.value = await api.orders(cleanParams({ ...orderFilters, limit: 100 }))
  })
}

async function markPaid(row) {
  await runAction(async () => {
    await api.markOrderPaid(field(row, 'orderNo', 'order_no'))
    await loadOrders()
  })
}

async function refund(row) {
  await runAction(async () => {
    await api.refundOrder(field(row, 'orderNo', 'order_no'))
    await loadOrders()
  })
}

async function setOrderStatus(row, status) {
  await runAction(async () => {
    await api.updateOrderStatus(field(row, 'orderNo', 'order_no'), { status })
    await loadOrders()
  })
}

async function loadRolePage() {
  await runAction(async () => {
    roles.value = await api.roles()
    const adminRows = await api.admins()
    admins.value = adminRows.map(row => ({
      ...row,
      roleIds: Array.isArray(field(row, 'roles')) ? field(row, 'roles').map(role => field(role, 'id')).filter(Boolean) : []
    }))
  })
}

function editRole(row) {
  copyTo(roleForm, normalize(row))
  const perms = (roleForm.permissions || '').split(',').map(s => s.trim()).filter(Boolean)
  checkedPermissionKeys.value = perms
  lastAutoCode.value = roleForm.code || ''
  roleDialog.value = true
  nextTick(() => {
    if (permissionTreeRef.value) {
      permissionTreeRef.value.setCheckedKeys(perms)
    }
  })
}

async function saveRole() {
  await runAction(async () => {
    const name = nullableText(roleForm.name)
    const code = nullableText(roleForm.code)
    const checked = checkedPermissionKeys.value.filter(k => allPermissionCodes.value.includes(k) || k === '*')
    const permissions = checked.length > 0 ? checked.join(',') : null
    if (!name) throw new Error(t('roleNameRequired'))
    if (!code) throw new Error(t('roleCodeRequired'))
    if (!permissions) throw new Error(t('rolePermissionsRequired'))
    await api.saveRole({
      id: roleForm.id,
      name,
      code,
      permissions,
      status: Number(roleForm.status ?? 1)
    })
    roleDialog.value = false
    checkedPermissionKeys.value = []
    await loadRolePage()
  })
}

async function removeRole(row) {
  await runAction(async () => {
    await ElMessageBox.confirm(t('deleteRoleConfirm'))
    await api.deleteRole(row.id)
    await loadRolePage()
  })
}

async function assignRoles(row) {
  await runAction(() => api.assignAdminRoles(row.id, row.roleIds || []))
}

function openCreateAdmin() {
  createAdminForm.username = ''
  createAdminForm.password = ''
  createAdminForm.nickname = ''
  createAdminDialog.value = true
}

async function submitCreateAdmin() {
  if (!createAdminForm.username || !createAdminForm.password) {
    ElMessage.warning(t('username') + ' 和 ' + t('password') + ' 必填')
    return
  }
  if (!validateUsername(createAdminForm.username)) {
    ElMessage.warning(t('usernameRule'))
    return
  }
  if (!validatePassword(createAdminForm.password)) {
    ElMessage.warning(t('passwordRule'))
    return
  }
  await runAction(async () => {
    await api.createAdmin({
      username: createAdminForm.username,
      password: createAdminForm.password,
      nickname: createAdminForm.nickname || createAdminForm.username
    })
    ElMessage.success(t('saved'))
    createAdminDialog.value = false
    await loadRolePage()
  })
}

function validatePassword(password) {
  return validatePasswordUtil(password)
}

function validateUsername(username) {
  return validateUsernameUtil(username)
}

function editAdmin(row) {
  editAdminForm.id = row.id
  editAdminForm.username = row.username || ''
  editAdminForm.nickname = row.nickname || ''
  editAdminForm.password = ''
  editAdminDialog.value = true
}

async function submitEditAdmin() {
  if (editAdminForm.password && !validatePassword(editAdminForm.password)) {
    ElMessage.warning(t('passwordRule'))
    return
  }
  const payload = { nickname: editAdminForm.nickname }
  if (editAdminForm.password) {
    payload.password = editAdminForm.password
  }
  await runAction(async () => {
    await api.updateAdmin(editAdminForm.id, payload)
    ElMessage.success(t('saved'))
    editAdminDialog.value = false
    await loadRolePage()
  })
}

async function confirmDeleteAdmin(row) {
  if (row.id === 1) {
    ElMessage.warning('不能删除超级管理员')
    return
  }
  try {
    await ElMessageBox.confirm(t('deleteConfirm'))
    await runAction(async () => {
      await api.deleteAdmin(row.id)
      ElMessage.success(t('delete'))
      await loadRolePage()
    })
  } catch (_) { /* cancelled */ }
}

async function loadLogs() {
  await runAction(async () => {
    logs.value = await api.operationLogs({ limit: 200 })
  })
}

function copyTo(target, source) {
  Object.keys(target).forEach(key => delete target[key])
  Object.assign(target, source)
}

function editCategory(row) {
  copyTo(categoryForm, { status: 1, ...row })
  categoryDialog.value = true
}

async function saveCategory() {
  await runAction(async () => {
    await api.saveCategoryFilter(categoryPayload())
    categoryDialog.value = false
    await loadAll()
  })
}

async function removeCategory(row) {
  await runAction(async () => {
    await ElMessageBox.confirm(t('deleteCategoryOptionConfirm'))
    await api.deleteCategoryFilter(row.id)
    await loadAll()
  })
}

function editDrama(row) {
  copyTo(dramaForm, normalize(row))
  dramaDialog.value = true
}

function newDramaDraft() {
  return {
    status: 1,
    sortOrder: 0,
    freeEpisodeCount: 5,
    totalEpisodes: 0,
    episodePricePoints: 10,
    wholePricePoints: 0,
    contentType: firstDramaFilterValue('contentType', 'real'),
    background: firstDramaFilterValue('background', 'modern'),
    theme: firstDramaFilterValue('theme', 'romance'),
    setting: firstDramaFilterValue('setting', 'ordinary'),
    audience: firstDramaFilterValue('audience', 'female'),
    recommended: false,
    // 新建短剧时无封面 objectKey,上传后由 uploadLocal 回填
    coverObjectKey: null,
    horizontalCoverObjectKey: null,
    verticalCoverObjectKey: null
  }
}

async function saveDrama() {
  await runAction(async () => {
    const payload = dramaPayload()
    const hadId = Boolean(payload.id)
    const saved = await api.saveDrama(payload)
    const dramaId = payload.id || saved?.id
    if (hadId && dramaId) {
      await api.applyFreePreview(dramaId, {
        freeCount: payload.freeEpisodeCount,
        pricePoints: payload.episodePricePoints
      })
    }
    dramaDialog.value = false
    await loadAll()
  })
}

function dramaPayload(source = dramaForm) {
  const title = nullableText(source.title)
  if (!title) throw new Error(t('dramaTitleRequired'))
  return {
    id: source.id,
    title,
    description: nullableText(source.description),
    coverUrl: nullableText(source.coverUrl),
    horizontalCoverUrl: nullableText(source.horizontalCoverUrl),
    verticalCoverUrl: nullableText(source.verticalCoverUrl),
    coverObjectKey: nullableText(source.coverObjectKey),
    horizontalCoverObjectKey: nullableText(source.horizontalCoverObjectKey),
    verticalCoverObjectKey: nullableText(source.verticalCoverObjectKey),
    tags: nullableText(source.tags),
    freeEpisodeCount: nonNegativeNumber(source.freeEpisodeCount, 0),
    episodePricePoints: nonNegativeNumber(source.episodePricePoints, 10),
    wholePricePoints: nonNegativeNumber(source.wholePricePoints, 0),
    totalEpisodes: nonNegativeNumber(source.totalEpisodes, 0),
    contentType: nullableText(source.contentType),
    background: nullableText(source.background),
    theme: nullableText(source.theme),
    setting: nullableText(source.setting),
    audience: nullableText(source.audience),
    publishDate: nullableText(source.publishDate),
    onlineTime: nullableText(source.onlineTime),
    hotScore: nonNegativeNumber(source.hotScore, 0),
    sortOrder: nonNegativeNumber(source.sortOrder, 0),
    recommended: Boolean(source.recommended),
    status: Number(source.status ?? 1)
  }
}

function nullableText(value) {
  if (value === null || value === undefined) return null
  const text = String(value).trim()
  return text || null
}

function nonNegativeNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? Math.trunc(number) : fallback
}

function integerNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.trunc(number) : fallback
}

async function removeDrama(row) {
  await runAction(async () => {
    await ElMessageBox.confirm(t('deleteDramaConfirm'))
    await api.deleteDrama(row.id)
    await loadAll()
  })
}

async function toggleDramaStatus(row) {
  await runAction(async () => {
    await api.saveDrama(dramaPayload({ ...normalize(row), status: Number(field(row, 'status')) === 1 ? 0 : 1 }))
    await loadAll()
  })
}

async function toggleRecommend(row) {
  await runAction(async () => {
    await api.saveDrama(dramaPayload({ ...normalize(row), recommended: Number(field(row, 'recommended')) !== 1 }))
    await loadAll()
  })
}

function openEpisodes(row) {
  episodeDramaId.value = row.id
  view.value = 'episode'
  loadEpisodes()
}

function newEpisodeDraft() {
  const nextNo = Math.max(0, ...episodes.value.map(item => Number(field(item, 'episodeNo', 'episode_no')) || 0)) + 1
  const freeCount = Number(field(selectedDrama.value, 'freeEpisodeCount', 'free_episode_count')) || 0
  const isFree = nextNo <= freeCount
  return {
    dramaId: episodeDramaId.value,
    episodeNo: nextNo,
    title: `${t('episode')} ${nextNo}`,
    status: 1,
    storageProvider: 'oss',
    accessType: isFree ? 'FREE' : 'POINTS',
    pricePoints: isFree ? 0 : (field(selectedDrama.value, 'episodePricePoints', 'episode_price_points') || 10),
    durationSeconds: 0,
    isFree,
    sortOrder: nextNo,
    // 新建分集时无封面 objectKey,上传后由 uploadLocal 回填
    coverObjectKey: null
  }
}

function editEpisode(row) {
  copyTo(episodeForm, normalize(row))
  // cloudflare 模式：videoUrl 字段复用为 uid 显示载体，把 cloudflareUid 镜像进去
  if (episodeForm.storageProvider === 'cloudflare' && episodeForm.cloudflareUid) {
    episodeForm.videoUrl = episodeForm.cloudflareUid
  }
  episodeDialog.value = true
}

// --- HLS 视频预览 & 转码状态 ---

// 转码状态标签表(一次性生成,避免每行多次调用函数)
const transcodeTagMap = computed(() => {
  const result = {}
  for (const row of episodes.value) {
    const id = field(row, 'id')
    if (!id) continue
    const cached = transcodeStatusMap[id]
    const status = cached !== undefined ? cached : field(row, 'transcodeStatus', 'transcode_status')
    if (status === null || status === undefined) continue
    const s = Number(status)
    if (s === 0) result[id] = { status: 0, text: '未转码', cls: 'tag-offline' }
    else if (s === 1) result[id] = { status: 1, text: '转码中', cls: 'tag-pending' }
    else if (s === 2) result[id] = { status: 2, text: 'HLS', cls: 'tag-online' }
    else if (s === -1) result[id] = { status: -1, text: '转码失败', cls: 'tag-error' }
  }
  return result
})

async function openVideoPreview(row) {
  videoPreview.visible = true
  videoPreview.loading = true
  videoPreview.error = null
  videoPreview.canRetry = false
  videoPreview.src = ''
  videoPreview.isHls = false
  videoPreview.transcodeStatus = 0
  videoPreview.row = row

  const epId = field(row, 'id')
  try {
    const status = await api.transcodeStatus(epId)
    const ts = Number(status?.status ?? 0)
    videoPreview.transcodeStatus = ts
    transcodeStatusMap[epId] = ts

    const hlsUrl = status?.hls_url || status?.hlsUrl
    const videoUrl = status?.video_url || status?.videoUrl || field(row, 'videoUrl', 'video_url')

    if (ts === 2 && hlsUrl) {
      // HLS 流播放
      videoPreview.isHls = true
      videoPreview.src = hlsUrl
      videoPreview.loading = false
      await nextTick()
      attachHlsPlayer(hlsUrl)
    } else if (ts === 1) {
      // 转码中,先播放原始视频
      videoPreview.isHls = false
      videoPreview.src = videoUrl || ''
      videoPreview.loading = false
      startTranscodePolling(epId)
    } else if (videoUrl) {
      // 未转码或转码失败,播放原始视频
      videoPreview.isHls = false
      videoPreview.src = videoUrl
      videoPreview.loading = false
      if (ts === -1) {
        videoPreview.canRetry = true
      }
    } else {
      videoPreview.loading = false
      videoPreview.error = '暂无可播放的视频'
    }
  } catch (e) {
    videoPreview.loading = false
    videoPreview.error = e.message || '获取视频信息失败'
  }
}

function attachHlsPlayer(url) {
  const video = videoPreviewRef.value
  if (!video) return

  // 清理之前的 HLS 实例
  if (videoPreviewHls) {
    videoPreviewHls.destroy()
    videoPreviewHls = null
  }

  if (Hls.isSupported()) {
    videoPreviewHls = new Hls({ maxBufferLength: 30 })
    videoPreviewHls.loadSource(url)
    videoPreviewHls.attachMedia(video)
    videoPreviewHls.on(Hls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        videoPreview.isHls = false
        videoPreview.error = 'HLS 播放失败: ' + (data.details || data.type)
        videoPreview.canRetry = true
      }
    })
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari 原生支持 HLS
    video.src = url
  } else {
    videoPreview.error = '当前浏览器不支持 HLS 播放'
  }
}

function closeVideoPreview() {
  if (videoPreviewHls) {
    videoPreviewHls.destroy()
    videoPreviewHls = null
  }
  stopTranscodePolling()
  videoPreview.src = ''
  videoPreview.error = null
  videoPreview.canRetry = false
  videoPreview.row = null
}

async function retryTranscode(row) {
  const epId = field(row, 'id')
  try {
    const res = await api.retranscode(epId)
    if (res?.success !== false) {
      ElMessage.success('已触发重新转码')
      transcodeStatusMap[epId] = 1
      // 如果视频预览弹窗打开,更新状态
      if (videoPreview.visible && videoPreview.row && field(videoPreview.row, 'id') === epId) {
        videoPreview.transcodeStatus = 1
        videoPreview.canRetry = false
        startTranscodePolling(epId)
      }
    } else {
      ElMessage.warning(res?.message || '重新转码失败')
    }
  } catch (e) {
    ElMessage.error(e.message || '重新转码失败')
  }
}

function startTranscodePolling(epId) {
  stopTranscodePolling()
  transcodePollTimer = setInterval(async () => {
    try {
      const status = await api.transcodeStatus(epId)
      const ts = Number(status?.status ?? 0)
      transcodeStatusMap[epId] = ts
      if (videoPreview.visible && videoPreview.row && field(videoPreview.row, 'id') === epId) {
        videoPreview.transcodeStatus = ts
        if (ts === 2 && (status?.hls_url || status?.hlsUrl)) {
          // 转码完成,切换到 HLS 播放
          stopTranscodePolling()
          const hlsUrl = status.hls_url || status.hlsUrl
          videoPreview.isHls = true
          videoPreview.src = hlsUrl
          nextTick(() => attachHlsPlayer(hlsUrl))
        } else if (ts === -1) {
          stopTranscodePolling()
          videoPreview.canRetry = true
          videoPreview.error = '转码失败,可点击重新转码'
        }
      } else {
        stopTranscodePolling()
      }
    } catch (e) {
      // 轮询失败,静默忽略
    }
  }, 5000)
}

function stopTranscodePolling() {
  if (transcodePollTimer) {
    clearInterval(transcodePollTimer)
    transcodePollTimer = null
  }
}

async function saveEpisode() {
  const isUploading = getUploadProgress('episode-videoUrl').uploading
  const hasVideoUrl = nullableText(episodeForm.videoUrl)
  const hasVideoFile = episodeForm._pendingFile

  if (isUploading || (hasVideoFile && !hasVideoUrl)) {
    const pe = reactive({
      _id: Date.now() + Math.random(),
      _status: 'uploading',
      _controller: uploadControllers['episode-videoUrl'] || null,
      progress: getUploadProgress('episode-videoUrl').percent || 0,
      speed: 0,
      errorReason: null,
      episodeNo: Number(episodeForm.episodeNo),
      title: nullableText(episodeForm.title) || `第${episodeForm.episodeNo}集`,
      dramaName: field(selectedDrama.value, 'title') || '',
      fileName: episodeForm._pendingFile?.name || episodeForm._pendingFile?.raw?.name || '',
      fileSize: episodeForm._pendingFile?.size || episodeForm._pendingFile?.raw?.size || 0,
      durationSeconds: episodeForm.durationSeconds || null,
      coverUrl: nullableText(episodeForm.coverUrl),
      coverObjectKey: nullableText(episodeForm.coverObjectKey),
      pricePoints: episodeForm.pricePoints,
      accessType: String(episodeForm.accessType || 'POINTS').toUpperCase(),
      storageProvider: 'r2',
      _file: episodeForm._pendingFile,
      _dramaId: Number(episodeForm.dramaId),
      _form: { ...episodeForm }
    })
    pendingEpisodes.push(pe)
    episodeDialog.value = false
    watchUploadProgress(pe, 'episode-videoUrl')
    return
  }

  await runAction(async () => {
    await api.saveEpisode(episodePayload())
    episodeDialog.value = false
    await loadAll()
  })
}

function episodePayload() {
  const dramaId = Number(episodeForm.dramaId)
  const episodeNo = Number(episodeForm.episodeNo)
  const title = nullableText(episodeForm.title)
  const storageProvider = nullableText(episodeForm.storageProvider) || 'oss'
  // cloudflare 模式：videoUrl 字段复用为 uid 显示载体，提交时分离到 cloudflareUid，videoUrl 留空
  const isCloudflare = storageProvider === 'cloudflare'
  const cloudflareUid = isCloudflare ? nullableText(episodeForm.videoUrl) || nullableText(episodeForm.cloudflareUid) : null
  const videoUrl = isCloudflare ? null : nullableText(episodeForm.videoUrl)
  if (!Number.isInteger(dramaId) || dramaId <= 0) throw new Error(t('episodeDramaRequired'))
  if (!Number.isInteger(episodeNo) || episodeNo <= 0) throw new Error(t('episodeNoRequired'))
  if (!title) throw new Error(t('episodeTitleRequired'))
  if (isCloudflare) {
    if (!cloudflareUid) throw new Error(t('episodeCloudflareUidRequired'))
  } else {
    if (!videoUrl) throw new Error(t('episodeVideoRequired'))
  }
  const accessType = String(episodeForm.accessType || 'POINTS').toUpperCase()
  return {
    id: episodeForm.id,
    dramaId,
    episodeNo,
    title,
    description: nullableText(episodeForm.description),
    coverUrl: nullableText(episodeForm.coverUrl),
    coverObjectKey: nullableText(episodeForm.coverObjectKey),
    videoUrl,
    cloudflareUid,
    durationSeconds: nonNegativeNumber(episodeForm.durationSeconds, 0),
    accessType,
    isFree: accessType === 'FREE',
    pricePoints: accessType === 'FREE' ? 0 : nonNegativeNumber(episodeForm.pricePoints, 10),
    sortOrder: nonNegativeNumber(episodeForm.sortOrder, episodeNo),
    storageProvider,
    status: Number(episodeForm.status ?? 1)
  }
}

async function removeEpisode(row) {
  await runAction(async () => {
    await ElMessageBox.confirm(t('deleteEpisodeConfirm'))
    await api.deleteEpisode(row.id)
    await loadEpisodes()
  })
}

async function applyFreePreview() {
  if (!selectedDrama.value) return
  await runAction(async () => {
    const freeCount = Number(field(selectedDrama.value, 'freeEpisodeCount', 'free_episode_count')) || 0
    const pricePoints = Number(field(selectedDrama.value, 'episodePricePoints', 'episode_price_points')) || 10
    await api.applyFreePreview(selectedDrama.value.id, { freeCount, pricePoints })
    await loadEpisodes()
    ElMessage.success(t('saved'))
  })
}

function accessTypeOf(row) {
  const type = field(row, 'accessType', 'access_type')
  if (type) return String(type).toUpperCase()
  return Number(field(row, 'isFree', 'is_free')) === 1 ? 'FREE' : 'POINTS'
}

function accessTypeLabel(row) {
  return {
    FREE: t('freePreview'),
    POINTS: t('paidEpisode')
  }[accessTypeOf(row)] || t('paidEpisode')
}

function accessTagClass(row) {
  return {
    FREE: 'tag-free',
    POINTS: 'tag-points'
  }[accessTypeOf(row)] || 'tag-points'
}

function openEpisodeAnalysis(row) {
  analyticsDramaId.value = field(row, 'dramaId', 'drama_id') || episodeDramaId.value
  view.value = 'analytics'
  loadAnalytics()
}

async function adjustEpisodePrice(row) {
  await runAction(async () => {
    const result = await ElMessageBox.prompt(t('adjustPricePrompt'), t('adjustPrice'), {
      inputValue: String(field(row, 'pricePoints', 'price_points') || 0),
      inputPattern: /^\d+$/,
      inputErrorMessage: t('priceInputError')
    })
    await api.saveEpisode({ ...normalize(row), pricePoints: Number(result.value), accessType: Number(result.value) === 0 ? 'FREE' : accessTypeOf(row) })
    await loadEpisodes()
  })
}

async function toggleEpisodeStatus(row) {
  await runAction(async () => {
    await api.saveEpisode({ ...normalize(row), status: Number(field(row, 'status')) === 1 ? 0 : 1 })
    await loadEpisodes()
  })
}

async function uploadUserAvatar(options) {
  const progressKey = 'user-avatar'
  setUploadProgress(progressKey, { uploading: true, percent: 0, error: null })
  uploadingUserAvatar.value = true
  try {
    const data = await api.uploadUserAvatar(options.file)
    userForm.avatarUrl = data?.url || ''
    options.onSuccess?.(data)
    setUploadProgress(progressKey, { uploading: false, percent: 100 })
    ElMessage.success(t('uploadSuccess'))
  } catch (err) {
    let reason = err.message || String(err)
    if (err.code === 'ECONNABORTED') reason = '上传超时，请检查网络后重试'
    else if (err.response?.status === 413) reason = '文件太大，超过服务器限制'
    else if (err.response?.status === 401) reason = '登录已过期，请重新登录'
    else if (err.response?.status === 403) reason = '没有上传权限'
    else if (err.response?.status >= 500) reason = '服务器内部错误，请查看后端日志'
    else if (!err.response && err.message === 'Network Error') reason = '网络连接失败，请检查域名和服务器是否正常'
    setUploadProgress(progressKey, { uploading: false, error: reason })
    options.onError?.(err)
    ElMessage.error(reason)
  } finally {
    uploadingUserAvatar.value = false
  }
}

// 封面 URL 字段名 → 对应 objectKey 字段名映射,上传成功后同步回填 objectKey,便于保存时透传给后端级联删 R2 对象
const COVER_OBJECT_KEY_FIELDS = {
  coverUrl: 'coverObjectKey',
  horizontalCoverUrl: 'horizontalCoverObjectKey',
  verticalCoverUrl: 'verticalCoverObjectKey'
}

function uploadLocal(options, target, fieldName, type, progressKey) {
  if (type === 'video' || fieldName === 'videoUrl') {
    target._pendingFile = options.file
    const rawFile = options.file?.raw || options.file
    if (rawFile) {
      const video = document.createElement('video')
      video.preload = 'metadata'
      video.onloadedmetadata = () => {
        if (video.duration && video.duration > 0 && target.durationSeconds !== undefined) {
          target.durationSeconds = Math.round(video.duration)
        }
        URL.revokeObjectURL(video.src)
      }
      video.src = URL.createObjectURL(rawFile)
    }
  }
  doUpload(options, target, fieldName, type, progressKey)
  return Promise.resolve()
}

function formatSpeed(bytesPerSec) {
  if (!bytesPerSec || bytesPerSec <= 0) return '0 KB/s'
  if (bytesPerSec < 1024) return `${bytesPerSec.toFixed(0)} B/s`
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(1)} KB/s`
  return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`
}

function formatFileSize(bytes) {
  if (!bytes || bytes <= 0) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function watchUploadProgress(pe, uploadKey) {
  const check = () => {
    const prog = getUploadProgress(uploadKey)
    pe.progress = prog.percent
    pe.speed = prog.speed
    if (prog.uploading) {
      setTimeout(check, 500)
    } else if (prog.error && prog.error !== '已取消') {
      pe._status = 'fail'
      pe.errorReason = prog.error
    } else if (!prog.error && prog.percent >= 100) {
      pe._form = pe._form || {}
      if (episodeForm.videoUrl && !pe._form.videoUrl) {
        pe._form.videoUrl = episodeForm.videoUrl
      }
      if (episodeForm.coverObjectKey && !pe._form.coverObjectKey) {
        pe._form.coverObjectKey = episodeForm.coverObjectKey
      }
      if (episodeForm.durationSeconds && !pe.durationSeconds) {
        pe.durationSeconds = episodeForm.durationSeconds
      }
      pe._status = 'creating'
      createPendingEpisode(pe)
    } else if (prog.error === '已取消') {
      pe._status = 'fail'
      pe.errorReason = '已取消'
    }
  }
  setTimeout(check, 500)
}

async function createPendingEpisode(pe) {
  try {
    const accessType = pe.accessType || 'POINTS'
    const payload = {
      dramaId: pe._dramaId,
      episodeNo: pe.episodeNo,
      title: pe.title,
      coverUrl: pe.coverUrl || null,
      coverObjectKey: pe.coverObjectKey || null,
      videoUrl: pe._form?.videoUrl || pe._form?.video_url || null,
      cloudflareUid: null,
      durationSeconds: pe.durationSeconds || 0,
      accessType,
      isFree: accessType === 'FREE',
      pricePoints: accessType === 'FREE' ? 0 : (pe.pricePoints || 10),
      storageProvider: 'r2',
      sortOrder: pe.episodeNo,
      status: 1
    }
    await api.saveEpisode(payload)
    const idx = pendingEpisodes.indexOf(pe)
    if (idx >= 0) pendingEpisodes.splice(idx, 1)
    await loadEpisodes()
    ElMessage.success(`第 ${pe.episodeNo} 集上传保存成功`)
  } catch (err) {
    pe._status = 'fail'
    pe.errorReason = err.message || '保存失败'
    ElMessage.error(`第 ${pe.episodeNo} 集保存失败：${err.message || err}`)
  }
}

function removePendingEpisode(pe) {
  const idx = pendingEpisodes.indexOf(pe)
  if (idx >= 0) pendingEpisodes.splice(idx, 1)
}

async function retryPendingEpisode(pe) {
  if (!pe._file) {
    ElMessage.warning('文件已丢失，请重新编辑上传')
    return
  }
  pe._status = 'uploading'
  pe.progress = 0
  pe.errorReason = null
  const controller = new AbortController()
  pe._controller = controller
  let lastTime = Date.now()
  let lastLoaded = 0
  const onProgress = (e) => {
    if (e.lengthComputable) {
      pe.progress = Math.min(99, Math.round((e.loaded / e.total) * 100))
      const now = Date.now()
      const dt = (now - lastTime) / 1000
      const dl = e.loaded - lastLoaded
      pe.speed = dt > 0 ? dl / dt : 0
      lastTime = now
      lastLoaded = e.loaded
    }
  }
  try {
    const rawFile = pe._file?.raw || pe._file
    const presign = await api.presignStorage(rawFile.name, rawFile.type || 'video/mp4', 'video')
    if (!presign || !presign.supported || !presign.presignedUrl) {
      throw new Error('R2 未启用，请检查服务器配置')
    }
    await api.putToPresignedUrl(presign.presignedUrl, rawFile, presign.headers, onProgress, controller.signal)
    pe._form = pe._form || {}
    pe._form.videoUrl = presign.url
    pe._status = 'creating'
    pe.progress = 100
    await createPendingEpisode(pe)
  } catch (err) {
    pe._status = 'fail'
    pe.errorReason = err.message === 'UPLOAD_CANCELLED' ? '已取消' : resolveUploadError(err)
  }
}

function cancelUpload(progressKey) {
  if (uploadControllers[progressKey]) {
    uploadControllers[progressKey].abort()
    delete uploadControllers[progressKey]
  }
  setUploadProgress(progressKey, { uploading: false, percent: 0, error: '已取消' })
}

async function doUpload(options, target, fieldName, type, progressKey) {
  const controller = new AbortController()
  uploadControllers[progressKey] = controller
  const fileName = options.file?.name || progressKey
  setUploadProgress(progressKey, { uploading: true, percent: 0, error: null, speed: 0 })
  let lastTime = Date.now()
  let lastLoaded = 0
  const onProgress = (e) => {
    if (e.lengthComputable) {
      const percent = Math.round((e.loaded / e.total) * 100)
      const now = Date.now()
      const dt = (now - lastTime) / 1000
      const dl = e.loaded - lastLoaded
      const speed = dt > 0 ? dl / dt : 0
      lastTime = now
      lastLoaded = e.loaded
      setUploadProgress(progressKey, { percent, speed })
    }
  }
  try {
    if (fieldName === 'videoUrl' && target.storageProvider === 'cloudflare') {
      target.storageProvider = 'r2'
      target.cloudflareUid = null
    }
    const isVideo = type === 'video' || fieldName === 'videoUrl'
    const R2_ALIASES = new Set(['cloudflare', 'oss', 'cos', 'r2'])
    const tryR2Presign = isVideo || R2_ALIASES.has(target.storageProvider)

    let presignError = null
    let presignData = null
    if (tryR2Presign) {
      try {
        const presign = await api.presignStorage(options.file.name, options.file.type, type)
        if (presign && presign.supported && presign.presignedUrl) {
          presignData = presign
        } else {
          const supported = presign && presign.supported
          presignError = supported === false
            ? 'R2 未启用或配置不完整。请检查服务器 .env 中 R2_ENABLED=true 及 CLOUDFLARE_R2_* 相关变量。'
            : '预签名接口返回异常,请检查后端日志。'
        }
      } catch (presignErr) {
        presignError = `请求 R2 预签名失败:${resolveUploadError(presignErr)}`
      }
    }

    if (presignData) {
      await api.putToPresignedUrl(presignData.presignedUrl, options.file, presignData.headers, onProgress, controller.signal)
      target[fieldName] = presignData.url
      if (fieldName === 'videoUrl') {
        target.storageProvider = 'r2'
        target.cloudflareUid = null
      }
      const coverObjectKeyField = COVER_OBJECT_KEY_FIELDS[fieldName]
      if (coverObjectKeyField) {
        target[coverObjectKeyField] = presignData.objectKey || null
      }
      options.onSuccess?.(presignData)
      setUploadProgress(progressKey, { uploading: false, percent: 100 })
      ElMessage.success(t('uploadSuccess'))
    } else if (isVideo) {
      const msg = `${presignError || 'R2 预签名直传未启用'}\n请检查服务器 .env 中 R2 配置,或使用「VPS中转上传」。`
      setUploadProgress(progressKey, { uploading: false, error: msg })
      options.onError?.(new Error(msg))
      ElMessage({ type: 'error', message: msg, duration: 10000 })
    } else {
      const data = await api.uploadStorage(options.file, type, onProgress)
      target[fieldName] = data.url
      const coverObjectKeyField = COVER_OBJECT_KEY_FIELDS[fieldName]
      if (coverObjectKeyField) target[coverObjectKeyField] = data.objectKey || null
      options.onSuccess?.(data)
      setUploadProgress(progressKey, { uploading: false, percent: 100 })
      ElMessage.success(t('uploadSuccess'))
    }
  } catch (err) {
    if (err.message === 'UPLOAD_CANCELLED') {
      setUploadProgress(progressKey, { uploading: false, percent: 0, error: '已取消' })
    } else {
      let reason = resolveUploadError(err)
      setUploadProgress(progressKey, { uploading: false, error: reason })
      options.onError?.(err)
      ElMessage({ type: 'error', message: reason, duration: 10000 })
    }
  } finally {
    delete uploadControllers[progressKey]
  }
}

async function runAction(action) {
  try {
    return await action()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err.message || String(err))
    }
    return null
  }
}

function normalize(row) {
  return {
    ...row,
    freeEpisodeCount: row.freeEpisodeCount ?? row.free_episode_count,
    totalEpisodes: row.totalEpisodes ?? row.total_episodes,
    contentType: row.contentType ?? row.content_type ?? 'real',
    background: row.background ?? firstDramaFilterValue('background', 'modern'),
    theme: row.theme ?? firstDramaFilterValue('theme', 'romance'),
    setting: row.setting ?? row.setting_key ?? firstDramaFilterValue('setting', 'ordinary'),
    audience: row.audience ?? firstDramaFilterValue('audience', 'female'),
    publishDate: row.publishDate ?? row.publish_date,
    hotScore: row.hotScore ?? row.hot_score ?? 0,
    horizontalCoverUrl: row.horizontalCoverUrl ?? row.horizontal_cover_url,
    verticalCoverUrl: row.verticalCoverUrl ?? row.vertical_cover_url,
    horizontalCoverObjectKey: row.horizontalCoverObjectKey ?? row.horizontal_cover_object_key,
    verticalCoverObjectKey: row.verticalCoverObjectKey ?? row.vertical_cover_object_key,
    episodePricePoints: row.episodePricePoints ?? row.episode_price_points,
    wholePricePoints: row.wholePricePoints ?? row.whole_price_points,
    onlineTime: normalizeDateTime(row.onlineTime ?? row.online_time),
    recommended: Boolean(Number(row.recommended ?? 0)),
    sortOrder: row.sortOrder ?? row.sort_order,
    dramaId: row.dramaId ?? row.drama_id,
    episodeNo: row.episodeNo ?? row.episode_no,
    coverUrl: row.coverUrl ?? row.cover_url,
    coverObjectKey: row.coverObjectKey ?? row.cover_object_key,
    videoUrl: row.videoUrl ?? row.video_url,
    cloudflareUid: row.cloudflareUid ?? row.cloudflare_uid,
    pricePoints: row.pricePoints ?? row.price_points,
    durationSeconds: row.durationSeconds ?? row.duration_seconds,
    isFree: Boolean(Number(row.isFree ?? row.is_free ?? 0)),
    accessType: row.accessType ?? row.access_type ?? (Number(row.isFree ?? row.is_free ?? 0) === 1 ? 'FREE' : 'POINTS'),
    storageProvider: row.storageProvider ?? row.storage_provider
  }
}

function normalizeDateTime(value) {
  if (!value) return value
  return String(value).replace(' ', 'T').slice(0, 19)
}

function cleanParams(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
}

function field(row, ...keys) {
  return fieldUtil(row, ...keys)
}

function sumRows(rows, ...keys) {
  return rows.reduce((total, row) => total + (Number(field(row, ...keys)) || 0), 0)
}

function stat(key) {
  return field(analytics.overview, key, toSnake(key))
}

function toSnake(key) {
  return key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
}

function formatNumber(value) {
  return formatNumberUtil(value, 'zh-CN')
}

function formatPercent(value) {
  return formatPercentUtil(value, 1)
}

function formatMoney(cents, currency) {
  return formatMoneyUtil(cents, currency, 'zh-CN')
}

function formatDuration(seconds) {
  return formatDurationUtil(seconds, 'zh-CN')
}

function formatDateTime(value) {
  return formatDateTimeUtil(value, 'zh-CN')
}

function userStatusLabel(status) {
  return Number(status) === 1 ? t('enabled') : t('disabled')
}

function statusLabel(status) {
  return Number(status) === 1 ? t('online') : t('offline')
}

function orderStatusLabel(status) {
  return {
    PENDING: t('pending'),
    PAID: t('paid'),
    REFUNDED: t('refunded'),
    CANCELLED: t('cancelled'),
    CLOSED: t('closed')
  }[status] || status || '-'
}

function orderStatusTagType(status) {
  return {
    PENDING: 'warning',
    PAID: 'success',
    REFUNDED: 'danger',
    CANCELLED: 'info',
    CLOSED: 'info'
  }[status] || 'info'
}

function shortDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(5)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}

function trendHeight(row) {
  const events = Number(field(row, 'playEvents', 'play_events')) || 0
  return `${Math.max(8, Math.round((events / maxTrendEvents.value) * 150))}px`
}

// --- Announcement methods ---
async function loadAnnouncements() {
  await runAction(async () => {
    announcements.value = await api.adminAnnouncements(cleanParams({ ...announcementFilters, limit: 100 }))
  })
}

function newAnnouncementDraft() {
  return {
    status: 'DRAFT',
    type: 'SYSTEM',
    isTop: 0,
    effectiveStart: null,
    effectiveEnd: null
  }
}

function editAnnouncement(row) {
  copyTo(announcementForm, normalize(row))
  announcementDialog.value = true
}

async function saveAnnouncement() {
  await runAction(async () => {
    const payload = {
      id: announcementForm.id,
      title: nullableText(announcementForm.title),
      content: nullableText(announcementForm.content),
      type: nullableText(announcementForm.type),
      isTop: Number(announcementForm.isTop ?? 0),
      effectiveStart: nullableText(announcementForm.effectiveStart),
      effectiveEnd: nullableText(announcementForm.effectiveEnd)
    }
    if (!payload.title) throw new Error(t('title') + ' 必填')
    if (!payload.content) throw new Error(t('content') + ' 必填')
    if (payload.id) {
      await api.updateAnnouncement(payload.id, payload)
    } else {
      await api.createAnnouncement(payload)
    }
    announcementDialog.value = false
    await loadAnnouncements()
  })
}

async function publishAnnouncementAction(row) {
  await runAction(async () => {
    await api.publishAnnouncement(row.id)
    await loadAnnouncements()
  })
}

async function unpublishAnnouncementAction(row) {
  await runAction(async () => {
    await api.updateAnnouncement(row.id, { status: 'DRAFT' })
    await loadAnnouncements()
  })
}

async function toggleAnnouncementTopAction(row) {
  await runAction(async () => {
    await api.toggleAnnouncementTop(row.id)
    await loadAnnouncements()
  })
}

async function confirmDeleteAnnouncement(row) {
  try {
    await ElMessageBox.confirm(t('confirmDeleteAnnouncement'))
    await runAction(async () => {
      await api.deleteAnnouncement(row.id)
      ElMessage.success(t('delete'))
      await loadAnnouncements()
    })
  } catch (_) { /* cancelled */ }
}

function announcementTypeLabel(type) {
  return {
    SYSTEM: t('systemAnnouncement'),
    ACTIVITY: t('activityAnnouncement')
  }[type] || type || '-'
}

function announcementStatusLabel(status) {
  return {
    DRAFT: t('draft'),
    PUBLISHED: t('published'),
    EXPIRED: t('expired')
  }[status] || status || '-'
}

function announcementStatusTagType(status) {
  return {
    DRAFT: 'info',
    PUBLISHED: 'success',
    EXPIRED: 'warning'
  }[status] || 'info'
}

// --- Feedback methods ---
async function loadFeedbacks() {
  await runAction(async () => {
    feedbacks.value = await api.adminFeedback(cleanParams({ ...feedbackFilters, limit: 100 }))
  })
}

function openFeedbackDetail(row) {
  feedbackDetailData.value = row
  feedbackDetailDialog.value = true
}

function openFeedbackReply(row) {
  if (!row) return
  feedbackReplyForm.id = row.id
  feedbackReplyForm.reply = field(row, 'adminReply') || ''
  feedbackReplyDialog.value = true
}

async function submitFeedbackReply() {
  await runAction(async () => {
    const id = feedbackReplyForm.id
    const reply = nullableText(feedbackReplyForm.reply)
    if (!id) throw new Error(t('feedbackDetail'))
    if (!reply) throw new Error(t('replyContent') + ' 必填')
    await api.replyFeedback(id, { reply })
    feedbackReplyDialog.value = false
    ElMessage.success(t('feedbackReplySuccess'))
    await loadFeedbacks()
  })
}

async function toggleFeedbackStatus(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      t('feedbackStatus') + '：' + t('processing') + ' / ' + t('processed') + ' / ' + t('closed'),
      t('feedbackStatus'),
      {
        inputValue: 'PROCESSING',
        inputPattern: /^(PROCESSING|PROCESSED|CLOSED)$/,
        inputErrorMessage: 'PROCESSING / PROCESSED / CLOSED'
      }
    )
    await runAction(async () => {
      await api.updateFeedbackStatus(row.id, value)
      await loadFeedbacks()
    })
  } catch (_) { /* cancelled */ }
}

async function confirmDeleteFeedback(row) {
  try {
    await ElMessageBox.confirm(t('confirmDeleteFeedback'))
    await runAction(async () => {
      await api.deleteFeedbackAdmin(row.id)
      ElMessage.success(t('delete'))
      await loadFeedbacks()
    })
  } catch (_) { /* cancelled */ }
}

function feedbackTypeLabel(type) {
  return {
    BUG: t('feedbackBug'),
    SUGGESTION: t('feedbackSuggestion'),
    CONSULTATION: t('feedbackConsultation'),
    COMPLAINT: t('feedbackComplaint'),
    OTHER: t('feedbackOther')
  }[type] || type || '-'
}

function feedbackTypeTagType(type) {
  return {
    BUG: 'danger',
    SUGGESTION: 'success',
    CONSULTATION: '',
    COMPLAINT: 'warning',
    OTHER: 'info'
  }[type] || 'info'
}

function feedbackStatusLabel(status) {
  return {
    PENDING: t('pending'),
    PROCESSING: t('processing'),
    PROCESSED: t('processed'),
    CLOSED: t('closed')
  }[status] || status || '-'
}

function feedbackStatusTagType(status) {
  return {
    PENDING: 'warning',
    PROCESSING: '',
    PROCESSED: 'success',
    CLOSED: 'info'
  }[status] || 'info'
}
</script>
