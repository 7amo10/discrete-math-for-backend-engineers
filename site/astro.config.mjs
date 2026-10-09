import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';

const isProd = process.env.NODE_ENV === 'production' || Boolean(process.env.GITHUB_ACTIONS);

// https://astro.build/config
export default defineConfig({
  site: 'https://7amo10.github.io',
  base: isProd ? '/discrete-math-for-backend-engineers' : '/',
  markdown: {
    remarkPlugins: [remarkMath],
    rehypePlugins: [rehypeKatex],
  },
  integrations: [
    starlight({
      title: 'Discrete Math for Backend Engineers',
      description: 'Bridging MIT 6.042J Discrete Mathematics directly to production Java systems',
      social: {
        github: 'https://github.com/7amo10/discrete-math-for-backend-engineers',
      },
      components: {
        Hero: './src/components/CustomHero.astro',
        PageFrame: './src/components/CustomPageFrame.astro',
      },
      customCss: [
        'katex/dist/katex.min.css',
        './src/styles/custom.css',
      ],
      sidebar: [
        {
          label: 'Foundations',
          items: [
            { label: 'Curriculum & Systems Overview', link: '/' },
          ],
        },
        {
          label: 'Week 1: Propositions & Invariants',
          items: [
            { label: 'Editorial: MIT 6.042J & Sagas', slug: 'week-01-invariants' },
            { label: 'Lab: Financial State Machine Challenge', slug: 'week-01-challenge' },
          ],
        },
        {
          label: 'Upcoming Discrete Modules',
          items: [
            { label: 'Week 2: Relations & Task Scheduling', slug: 'week-02-posets-scheduling' },
            { label: 'Week 3: Graph Theory & Routing', slug: 'week-03-graph-topologies' },
            { label: 'Week 4: Number Theory & Hashing', slug: 'week-04-modular-hashing' },
          ],
        },
      ],
    }),
  ],
});
