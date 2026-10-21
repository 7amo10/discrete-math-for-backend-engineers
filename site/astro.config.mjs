import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import { visit } from 'unist-util-visit';

const SITE_BASE = '/discrete-math-for-backend-engineers';

/**
 * Automatically rewrites internal root-relative links in Markdown/MDX
 * to include the base path, preventing 404s on GitHub Pages and unifying
 * local dev and deployed environments.
 */
function rehypePrefixBase() {
  return (tree) => {
    visit(tree, 'element', (node) => {
      if (node.tagName === 'a' && node.properties && typeof node.properties.href === 'string') {
        const href = node.properties.href;
        if (
          href.startsWith('/') &&
          !href.startsWith(SITE_BASE) &&
          !href.startsWith('//')
        ) {
          node.properties.href = `${SITE_BASE}${href}`;
        }
      }
    });
  };
}

// https://astro.build/config
export default defineConfig({
  site: 'https://7amo10.github.io',
  base: SITE_BASE,
  markdown: {
    remarkPlugins: [remarkMath],
    rehypePlugins: [rehypeKatex, rehypePrefixBase],
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
            { label: 'Overview & Systems Intro', link: '/' },
            { label: 'Curriculum & Modules Index', slug: 'curriculum' },
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
          label: 'Week 2: Number Theory & Modular Hashing',
          items: [
            { label: 'Editorial: Modular Rings & Consistent Hashing', slug: 'week-02-modular-hashing' },
            { label: 'Lab: Hash Ring & Vnodes Challenge', slug: 'week-02-challenge' },
          ],
        },
        {
          label: 'Week 3: Graph Topologies & Cycles',
          items: [
            { label: 'Editorial: Wait-For Graphs & Tarjan SCC', slug: 'week-03-graph-topologies' },
            { label: 'Lab: Deadlock Engine & Victim Selection', slug: 'week-03-challenge' },
          ],
        },
        {
          label: 'Upcoming Discrete Modules',
          items: [
            { label: 'Week 4: Relations & Task Scheduling', slug: 'week-04-posets-scheduling' },
          ],
        },
      ],
    }),
  ],
});
