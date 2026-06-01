import Phaser from 'phaser';

export default class BattleScene extends Phaser.Scene {
    constructor() {
        super({ key: 'BattleScene' });
    }

    create() {
        this.add.text(400, 300, '战斗场景占位符', {
            fontSize: '32px',
            fill: '#fff'
        }).setOrigin(0.5);
    }
}