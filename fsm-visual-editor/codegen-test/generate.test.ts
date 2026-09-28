import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { it } from 'vitest';
import { generateJavaFactory, generateKotlinFactory, createEmptyDocument } from '../src/domain';

it('exports real factory sources for the Gradle compilation and execution test', async () => {
  for (const style of ['fluent', 'builder'] as const) {
    for (const language of ['java', 'kotlin'] as const) {
      const document = createEmptyDocument();
      document.codegen = { ...document.codegen, packageName: 'generated', className: `${language}_${style}`,
        stateType: 'State', eventType: 'Event', domainType: 'Order', initialState: 'NEW', style };
      document.maxImmediateAutoTransitions = 2;
      document.states = ['NEW', 'READY', 'DONE'].map((label) => ({ id: label, label, position: { x: 0, y: 0 } }));
      document.events = [{ id: 'FINISH' }];
      document.transitions = [
        { id: 'auto', from: 'NEW', to: 'READY', trigger: { kind: 'auto' }, conditions: [], actions: [], postActions: [], autoTransitionEnabled: true },
        { id: 'finish', from: 'READY', to: 'DONE', trigger: { kind: 'event', event: 'FINISH' }, conditions: [], actions: [], postActions: [], timeout: { value: 1, unit: 'MILLISECONDS' } },
      ];
      const directory = resolve('codegen-test/build/generated', language);
      await mkdir(directory, { recursive: true });
      await writeFile(resolve(directory, `${document.codegen.className}.${language === 'java' ? 'java' : 'kt'}`),
        language === 'java' ? generateJavaFactory(document) : generateKotlinFactory(document));
    }
  }
});
