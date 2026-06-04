const API_BASE = '/api/game';

const app = Vue.createApp({
  data() {
    return {
      state: null,
      loading: false,
      error: null,
      showCardConfirm: false,
      selectedCard: null,
      selectedIndex: -1,
      // 地图相关
      isFromMap: false,
      prevNodeId: null,
      nextNodeId: null
    };
  },

  mounted() {
    const params = new URLSearchParams(window.location.search);
    if (params.get('from') === 'map') {
      this.isFromMap = true;
      this.prevNodeId = params.get('prevNode') || 'start';
      this.nextNodeId = params.get('nextNode');
    }
  },

  computed: {
    hand() {
      return this.state?.hand || [];
    }
  },

  methods: {
    canPlayCard(card) {
      if (!this.state || !card) return false;
      return card.cost <= this.state.energy;
    },

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
        this.error = e.message.includes('Failed to fetch')
          ? '无法连接后端。请确认后端已启动。'
          : '无法开始新战斗：' + e.message;
        console.error(e);
      } finally {
        this.loading = false;
      }
    },

    previewCard(index) {
      if (!this.state) {
        this.error = '请先开始新战斗';
        return;
      }
      if (index < 0 || index >= this.hand.length) {
        this.error = '无效的卡牌编号';
        return;
      }
      const card = this.hand[index];
      if (!this.canPlayCard(card)) {
        this.error = `能量不足！该卡牌需要 ${card.cost} 点能量，当前只有 ${this.state.energy} 点`;
        return;
      }
      this.selectedCard = card;
      this.selectedIndex = index;
      this.showCardConfirm = true;
    },

    async confirmPlay() {
      if (this.selectedIndex < 0) return;
      await this.playCard(this.selectedIndex);
      this.showCardConfirm = false;
      this.selectedCard = null;
      this.selectedIndex = -1;
    },

    cancelPlay() {
      this.showCardConfirm = false;
      this.selectedCard = null;
      this.selectedIndex = -1;
    },

    async playCard(index) {
      this.error = null;
      if (!this.state || index < 0 || index >= this.hand.length) {
        this.error = '请先开始新战斗或选择有效卡牌';
        return;
      }
      try {
        const resp = await fetch(`${API_BASE}/play?index=${encodeURIComponent(index)}`, {
          method: 'POST'
        });
        const data = await resp.json();
        if (!resp.ok) {
          this.error = data.error || `请求失败 ${resp.status}`;
          return;
        }
        this.state = data;
      } catch (e) {
        this.error = e.message.includes('Failed to fetch')
          ? '无法连接后端。请确认后端已启动。'
          : '打出卡牌失败：' + e.message;
        console.error(e);
      }
    },

    async endTurn() {
      this.error = null;
      if (!this.state || this.state.gameOver) {
        this.error = !this.state ? '请先开始新战斗' : '战斗已结束';
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
        this.error = e.message.includes('Failed to fetch')
          ? '无法连接后端。请确认后端已启动。'
          : '结束回合失败：' + e.message;
        console.error(e);
      }
    },

    // 战斗结束后的处理：同步血量，胜利进奖励页，失败回地图
            goAfterFight() {
      if (this.state && this.state.playerHp !== undefined) {
        localStorage.setItem('playerHP', this.state.playerHp);
      }
      if (window.updateStatusBar) window.updateStatusBar();

      const charParam = new URLSearchParams(window.location.search).get('char') || '1';

      if (this.isFromMap && this.state && (this.state.winner === 'player' || this.state.winner === 'Player' || (this.state.gameOver && this.state.playerHp > 0))) {
        const nodeType = new URLSearchParams(window.location.search).get('nodeType') || 'monster';
        const prev = this.prevNodeId || 'start';
        const next = this.nextNodeId || prev;
        window.location.href = `reward.html?source=fight&char=${charParam}&prevNode=${prev}&nextNode=${next}&nodeType=${nodeType}&gotCard=0`;
      } else {
        const target = this.isFromMap ? (this.prevNodeId || 'start') : 'start';
        window.location.href = `map.html?char=${charParam}&currentNode=${target}`;
      }
    }
  }
});

app.mount('#app');