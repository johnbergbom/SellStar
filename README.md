# SellStar
SellStar Integration Platform is a business application for doing drop shipping. The application was developed as part of a project for selling items on the Internet. For about two years it powered a very large amount of sales for us. The application likely no longer works, since the project has been abandoned many years ago. Back in the days, this project was used to run the top selling store at the Finnish auction site https://www.huuto.net. The aim of the application is to automate 95% of the manual tasks associated with dop shipping based business. It could fully automatically handle about 80% of the orders without any manual intervention whatsoever, and it did:

1. Place ads for items at https://www.huuto.net.
2. Read its email box to see if some buyer had bought some item (https://www.huuto.net sends out emails to the seller once a winning bid has come).
3. Extract the relevant information from the email and send payment instructions to the buyer.
4. Login to the bank to see if the money has come.
5. Order the item from a Chinese drop shipping partner to be delivered directly to the customer.
6. Provide the customer with continual updates on the status of shipping (for packets were tracking is used)

Other features:
- Integration with several different stores (though https://www.huuto.net was the biggest one, a few others were used as well)
- Integration with several different Chinese drop shipping partners
- Tracking of packets
- Sending updates on tracking to customers
- Simple interface for handling customer inquiries
- Statistics interface
- Extraction of sales data for book keeping purposes
- Automatic handling of many faulty payments (e.g. when the wrong reference number was used)
- Powerful interface for manually handling such faulty payments that can't be taken care of automatically
- Refunding
- Logic for automatically allocating one bulk payment to multiple different orders
- Logic for automatically combining multiple orders into one packet when ordering from the drop shipping partner
- Good heuristics for extracting relevant data from customer provided data that's often faulty or inconcistent
- Etc
