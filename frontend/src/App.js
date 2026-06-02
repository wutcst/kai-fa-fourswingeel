import React, { useState } from 'react';

function App() {
    // 战斗状态
    const [battleLog, setBattleLog] = useState('');
    const [handCards, setHandCards] = useState([]);
    const [playerHp, setPlayerHp] = useState(null);
    const [playerBlock, setPlayerBlock] = useState(0);
    const [enemyHp, setEnemyHp] = useState(null);
    const [enemyName, setEnemyName] = useState(null);
    const [gameOver, setGameOver] = useState(false);
    const [winner, setWinner] = useState(null);
    const [inputIndex, setInputIndex] = useState('');

    const startBattle = async () => {
        try {
            const res = await fetch('/api/game/new', { method: 'POST' });
            const data = await res.json();
            updateState(data);
        } catch (err) {
            setBattleLog('启动战斗失败: ' + err.message);
        }
    };

    const playCard = async () => {
        const idx = parseInt(inputIndex, 10);
        if (isNaN(idx)) {
            alert('请输入有效的编号');
            return;
        }
        try {
            const res = await fetch(`/api/game/play?index=${idx}`, { method: 'POST' });
            const data = await res.json();
            updateState(data);
        } catch (err) {
            setBattleLog('出牌失败: ' + err.message);
        }
    };

    const updateState = (data) => {
        setPlayerHp(data.playerHp);
        setPlayerBlock(data.playerBlock || 0);
        setEnemyHp(data.enemyHp);
        setEnemyName(data.enemyName);
        setHandCards(data.handCards || []);
        setGameOver(data.gameOver);
        setWinner(data.winner);
        const log = data.log && data.log.join('\n');
        setBattleLog(log || (data.gameOver ? `战斗结束，${data.winner}赢了！` : ''));
        setInputIndex('');
    };

    return (
        <div style={{ textAlign: 'center', marginTop: '20px' }}>
            <h1>杀戮尖塔·课程项目</h1>
            <div style={{ margin: '20px auto', maxWidth: '600px', textAlign: 'left' }}>
                <h2>战斗面板</h2>
                <div>
                    <button onClick={startBattle} disabled={playerHp !== null && !gameOver}>
                        开始战斗
                    </button>
                </div>
                {playerHp !== null && (
                    <>
                        <p>玩家 HP: {playerHp} (格挡: {playerBlock}) | {enemyName} HP: {enemyHp}</p>
                        <h3>你的手牌</h3>
                        <ul>
                            {handCards.map(card => (
                                <li key={card.index}>
                                    [{card.index}] {card.name} {card.type === 'ATTACK' ? `⚔️${card.damage}` : `🛡️${card.block}`}
                                </li>
                            ))}
                        </ul>
                        {!gameOver && (
                            <div>
                                <input
                                    type="number"
                                    value={inputIndex}
                                    onChange={e => setInputIndex(e.target.value)}
                                    placeholder="输入卡牌编号"
                                    style={{ width: '150px', marginRight: '10px' }}
                                />
                                <button onClick={playCard}>出牌</button>
                            </div>
                        )}
                        {gameOver && <p><strong>结果: {winner}赢了！</strong></p>}
                    </>
                )}
                {battleLog && (
                    <pre style={{ background: '#f0f0f0', padding: '10px', borderRadius: '5px', whiteSpace: 'pre-wrap' }}>
                        {battleLog}
                    </pre>
                )}
            </div>
        </div>
    );
}

export default App;
