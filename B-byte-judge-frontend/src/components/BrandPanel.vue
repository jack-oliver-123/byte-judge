<script setup lang="ts">
import { Check, Code2, Layers, Trophy, Zap } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
</script>

<template>
  <section class="story" aria-label="Byte Judge 编程竞赛管理平台">
    <div class="story-copy">
      <p class="eyebrow">BYTE JUDGE · 竞赛管理平台</p>
      <h1>
        让好题，<br /><span>被看见<i aria-hidden="true"></i></span>
      </h1>
      <p class="intro">面向编程竞赛的题库、赛事与评测管理平台。</p>
      <div class="feature-chips">
        <Badge variant="outline"><Code2 />题库编排</Badge>
        <Badge variant="outline"><Trophy />赛事管理</Badge>
        <Badge variant="outline"><Zap />实时评测</Badge>
      </div>
    </div>
    <div class="code-scene" aria-label="Java 21 代码与评测动效示意，非实时状态">
      <span class="lime-orb" aria-hidden="true"></span>
      <svg class="orbit" viewBox="0 0 650 250" fill="none" aria-hidden="true">
        <path
          d="M-30 182C42 60 190 120 300 170S520 280 680 0"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-dasharray="3 6"
        />
      </svg>
      <div class="code-card">
        <div class="code-head">
          <div class="window-dots" aria-hidden="true"><i></i><i></i><i></i></div>
          <span class="code-language">Main.java</span>
          <Badge class="accepted"><Check :size="14" />Accepted</Badge>
        </div>
        <div class="code-lines" aria-hidden="true">
          <div>
            <em>1</em><code><b>class</b> Main {</code>
          </div>
          <div>
            <em>2</em><code> <b>public static void</b> main(String[] args) {</code>
          </div>
          <div>
            <em>3</em><code> System.out.println(<span>"Hello, Byte Judge!"</span>);</code>
          </div>
          <div><em>4</em><code> }</code></div>
          <div><em>5</em><code>}</code><span class="code-cursor"></span></div>
        </div>
      </div>
      <div class="sample-chip">
        <Layers :size="19" /><span>样例测试</span><strong>通过</strong><Check :size="16" />
      </div>
      <svg class="spark" viewBox="0 0 44 40" aria-hidden="true">
        <path
          d="m7 17 1-13m12 18 8-13m-1 24 13-5"
          fill="none"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-width="3"
        />
      </svg>
    </div>
  </section>
</template>

<style scoped>
.story {
  position: relative;
  min-width: 0;
}
.story::before {
  position: absolute;
  z-index: 0;
  top: 8px;
  right: 0;
  width: 190px;
  height: 280px;
  background-image:
    linear-gradient(#e6e3db80 1px, transparent 1px),
    linear-gradient(90deg, #e6e3db80 1px, transparent 1px);
  background-size: 26px 26px;
  mask-image: radial-gradient(ellipse, #000 25%, transparent 73%);
  content: '';
}
.story-copy {
  position: relative;
  z-index: 1;
}
.eyebrow {
  margin: 0 0 20px;
  color: #505b6d;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 2.4px;
}
h1 {
  margin: 0;
  font-size: clamp(48px, 5.5vw, 78px);
  font-weight: 800;
  letter-spacing: -4px;
  line-height: 1.17;
}
h1 span {
  position: relative;
  z-index: 0;
  display: inline-block;
}
h1 i {
  position: absolute;
  z-index: -1;
  bottom: 5px;
  left: 2px;
  width: 103%;
  height: 12px;
  background: var(--lime);
  border-radius: 20px;
  transform: rotate(-5deg);
  transform-origin: left;
  animation: marker-in 850ms 300ms var(--ease-out) both;
}
.intro {
  margin: 26px 0 20px;
  color: #535c6b;
  font-size: 15px;
  line-height: 1.8;
}
.feature-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.feature-chips :deep([data-slot='badge']) {
  gap: 9px;
  height: 35px;
  padding: 0 14px;
  border-radius: 9px;
  background: #ffffff55;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 3px #15191e02;
}
.feature-chips svg {
  width: 16px;
  height: 16px;
}
.code-scene {
  position: relative;
  width: 100%;
  max-width: 580px;
  height: 205px;
  margin-top: 24px;
  perspective: 1000px;
}
.lime-orb {
  position: absolute;
  top: -8px;
  left: 5px;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: var(--lime);
  box-shadow: inset -15px -12px 30px #b6e43b;
  animation: orb-drift 9s ease-in-out infinite;
}
.orbit {
  position: absolute;
  top: 0;
  left: -65px;
  width: calc(100% + 130px);
  height: 240px;
  color: var(--primary);
  overflow: visible;
}
.orbit path {
  animation: orbit-dash 12s linear infinite;
}
.code-card {
  position: absolute;
  top: 22px;
  left: 28px;
  width: calc(100% - 50px);
  border: 1px solid #dfddd7;
  border-radius: 16px;
  background: #ffffffed;
  box-shadow:
    0 20px 38px -18px #26291f35,
    0 5px 10px #26291f06;
  transform: rotate(-4deg);
  animation: card-float 7s ease-in-out infinite;
}
.code-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 19px 12px;
}
.window-dots {
  display: flex;
  gap: 7px;
}
.window-dots i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ff6058;
}
.window-dots i:nth-child(2) {
  background: #ffbf2f;
}
.window-dots i:nth-child(3) {
  background: #27c840;
}
.code-language {
  color: #92958d;
  font:
    10px Consolas,
    monospace;
}
.accepted {
  margin-left: auto;
  gap: 5px;
  border: 0;
  border-radius: 8px;
  background: var(--lime);
  color: #23350b;
  font-size: 11px;
}
.code-lines {
  padding: 0 16px 18px;
  font:
    12px/1.9 Consolas,
    monospace;
}
.code-lines > div {
  display: flex;
}
.code-lines em {
  width: 23px;
  flex-shrink: 0;
  color: #9398a3;
  font-size: 11px;
  font-style: normal;
}
.code-lines code {
  white-space: pre;
}
.code-lines b {
  color: var(--primary);
  font-weight: 500;
}
.code-lines code span {
  color: #cf4844;
}
.code-cursor {
  align-self: center;
  width: 6px;
  height: 12px;
  margin-left: 6px;
  background: var(--primary);
  animation: caret-blink 1.2s steps(1) infinite;
}
.sample-chip {
  position: absolute;
  right: 0;
  bottom: 10px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 14px 17px;
  border: 1px solid #a8e9d7;
  border-radius: 12px;
  background: #d2fbef;
  color: #183e36;
  font-size: 12px;
  box-shadow:
    0 7px 15px #164a3420,
    inset 0 1px 0 #fff9;
  transform: rotate(-4deg);
  animation: chip-float 7s ease-in-out infinite;
}
.sample-chip strong {
  margin-left: 4px;
  font-weight: 650;
}
.spark {
  position: absolute;
  top: -24px;
  right: 8px;
  width: 40px;
  color: var(--primary);
}
@keyframes card-float {
  0%,
  100% {
    transform: translateY(0) rotate(-4deg);
  }
  50% {
    transform: translateY(-9px) rotate(-2.5deg);
  }
}
@keyframes chip-float {
  0%,
  100% {
    transform: translateY(0) rotate(-4deg);
  }
  50% {
    transform: translateY(5px) rotate(-2deg);
  }
}
@keyframes orb-drift {
  50% {
    transform: translate(10px, -10px) scale(1.04);
  }
}
@keyframes orbit-dash {
  to {
    stroke-dashoffset: -180;
  }
}
@keyframes caret-blink {
  50% {
    opacity: 0;
  }
}
@keyframes marker-in {
  from {
    transform: scaleX(0) rotate(-5deg);
  }
  to {
    transform: scaleX(1) rotate(-5deg);
  }
}
@media (max-width: 1100px) {
  .code-lines {
    font-size: 10px;
  }
  .intro {
    font-size: 13px;
  }
  .feature-chips {
    gap: 7px;
  }
  .feature-chips :deep([data-slot='badge']) {
    padding: 0 10px;
  }
}
@media (max-width: 800px) {
  .story {
    padding: 0;
  }
  h1 {
    font-size: 46px;
    letter-spacing: -2px;
  }
  .intro {
    margin: 18px 0;
  }
  .code-scene {
    display: none;
  }
  .story::before {
    height: 200px;
  }
}
</style>
