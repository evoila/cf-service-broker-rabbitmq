import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { RabbitMqComponent } from './rabbitmq.component';

export const ROUTES = [
  {
    path: '',
    component: RabbitMqComponent,
    pathMatch: 'full'
  }
];

@NgModule({
  imports: [RouterModule.forChild(ROUTES)],
  exports: [RouterModule]
})
export class RabbitMqRoutingModule { }
