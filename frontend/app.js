const API_BASE = '/api/game';  // 同源时使用相对路径，避免 CORS 问题

const app = Vue.createApp({
  data() {
    return {
      state: null,   // 当前战斗状态
      loading: false,
      error: null,
      // 卡牌确认对话框
      showCardConfirm: false,
      selectedCard: null,
      selectedIndex: -1
    };
  },

  computed: {
    hand() {
      // 后端返回的 hand 数组，每项至少包含 name / cost
      return this.state?.hand || [];
    }
  },

  methods: {
    async newGame() {
      this.error = null;
      this.loading = true;
      try {
        const resp = await fetch(`${API_BASE}/new`, { method: 'POST' });
        if (!resp.ok) {
          const text = await resp.text();
          throw new Error(`请求失败 ${resp.status}: ${text}`);
        }
        this.state = await resp.json();
      } catch (e) {
        if (e.message && e.message.includes('Failed to fetch')) {
          this.error = '无法连接后端。请确认后端已启动，并通过 http://localhost:8080/index.html 访问本页面（将前端文件放入后端的 src/main/resources/static 文件夹下）。';
        } else {
          this.error = '无法开始新战斗：' + e.message;
        }
        console.error(e);
      } finally {
        this.loading = false;
      }
    },

    // 预览卡牌效果，弹出确认对话框
    previewCard(index) {
      if (!this.state) {
        this.error = '请先开始新战斗';
        return;
      }
      if (index < 0 || index >= this.hand.length) {
        this.error = '无效的卡牌编号';
        return;
      }
      this.selectedCard = this.hand[index];
      this.selectedIndex = index;
      this.showCardConfirm = true;
    },

    // 确认打出当前预览的卡牌
    async confirmPlay() {
      if (this.selectedIndex < 0) return;
      await this.playCard(this.selectedIndex);
      this.showCardConfirm = false;
      this.selectedCard = null;
      this.selectedIndex = -1;
    },

    // 取消打出
    cancelPlay() {
      this.showCardConfirm = false;
      this.selectedCard = null;
      this.selectedIndex = -1;
    },

    // 实际打出卡牌（内部方法，也可由其他逻辑调用）
    async playCard(index) {
      this.error = null;
      if (!this.state) {
        this.error = '请先开始新战斗';
        return;
      }
      if (index < 0 || index >= this.hand.length) {
        this.error = '无效的卡牌编号';
        return;
      }
      try {
        const resp = await fetch(`${API_BASE}/play?index=${encodeURIComponent(index)}`, {
          method: 'POST'
        });
        if (!resp.ok) {
          const text = await resp.text();
          throw new Error(`请求失败 ${resp.status}: ${text}`);
        }
        this.state = await resp.json();
      } catch (e) {
        if (e.message && e.message.includes('Failed to fetch')) {
          this.error = '无法连接后端。请确认后端已启动，并通过 http://localhost:8080/index.html 访问本页面。';
        } else {
          this.error = '打出卡牌失败：' + e.message;
        }
        console.error(e);
      }
    },

    async endTurn() {
      this.error = null;
      if (!this.state) {
        this.error = '请先开始新战斗';
        return;
      }
      if (this.state.gameOver) {
        this.error = '战斗已结束';
        return;
      }
      try {
        const resp = await fetch(`${API_BASE}/endTurn`, { method: 'POST' });
        if (!resp.ok) {
          const text = await resp.text();
          throw new Error(`请求失败 ${resp.status}: ${text}`);
        }
        this.state = await resp.json();
      } catch (e) {
        if (e.message && e.message.includes('Failed to fetch')) {
          this.error = '无法连接后端。请确认后端已启动，并通过 http://localhost:8080/index.html 访问本页面。';
        } else {
          this.error = '结束回合失败：' + e.message;
        }
        console.error(e);
      }
    }
  }
});

app.mount('#app');
