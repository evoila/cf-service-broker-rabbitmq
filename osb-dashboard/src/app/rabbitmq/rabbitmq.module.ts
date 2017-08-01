import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RabbitMqComponent } from './rabbitmq.component';
import { RabbitMqRoutingModule } from './rabbitmq-routing.module';

@NgModule({
  imports: [
    CommonModule,
    RabbitMqRoutingModule
  ],
  declarations: [RabbitMqComponent]
})
export class RabbitMqModule { }
