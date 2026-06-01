import React, { useEffect } from 'react';
import Phaser from 'phaser';
import BattleScene from './scenes/BattleScene';

function App() {
    useEffect(() => {
        const config = {
            type: Phaser.AUTO,
            parent: 'game-container',
            width: 800,
            height: 600,
            scene: BattleScene,
            physics: { default: 'arcade', arcade: { debug: false } }
        };
        const game = new Phaser.Game(config);
        return () => game.destroy(true);
    }, []);

    return (
        <div style={{ textAlign: 'center', marginTop: '20px' }}>
            <h1>杀戮尖塔·课程项目</h1>
            <div id="game-container"></div>
            <p>React UI 区域（后续开发）</p>
        </div>
    );
}

export default App;